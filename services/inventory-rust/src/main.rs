//! # Inventory Service (Rust/Axum)
//!
//! Manages stock levels for all products. Supports single-item and batch
//! reservation/release operations, each within an atomic DB transaction.
//!
//! ## Idempotency
//! A `reservations` table with a unique constraint on `(order_id, product_id)`
//! prevents double-reservation when Kafka re-delivers an `order-created` event.
//! Batch operations check for existing reservations before modifying stock.
//!
//! ## Saga Integration
//! Called by the orchestrator during the order fulfillment saga:
//! - **reserve** — deduct stock when an order is placed
//! - **release** — restore stock when a saga fails (compensating transaction)
//!
//! ## Endpoints
//! - `GET /inventory` — list all inventory items
//! - `POST /inventory/reserve` — reserve one product (deduct quantity)
//! - `POST /inventory/release` — release one product (restore quantity)
//! - `POST /inventory/batch-reserve` — reserve multiple products in one transaction
//! - `POST /inventory/batch-release` — release multiple products in one transaction

mod entity;
mod migrator;
mod reservation;

use axum::{
    body::Body,
    extract::{Path, State},
    http::{HeaderValue, Method, Request, Response, StatusCode},
    middleware::Next,
    routing::{get, post},
    Json, Router,
};
use entity::Entity as InventoryItem;
use migrator::Migrator;
use reservation::Entity as Reservation;
use sea_orm::{
    ActiveModelTrait, ColumnTrait, Database, DatabaseConnection, EntityTrait, QueryFilter,
    Set, TransactionTrait,
};
use sea_orm_migration::MigratorTrait;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::net::TcpListener;

/// Request body for reserving/releasing a single product.
#[derive(Serialize, Deserialize)]
struct ReservationRequest {
    order_id: String,
    product_id: String,
    quantity: i32,
}

/// One item within a batch reservation/release request.
#[derive(Serialize, Deserialize)]
struct BatchReservationItem {
    product_id: String,
    quantity: i32,
}

/// Request body for reserving/releasing multiple products at once.
#[derive(Serialize, Deserialize)]
struct BatchReservationRequest {
    order_id: String,
    items: Vec<BatchReservationItem>,
}

/// Response returned by all reservation/release endpoints.
#[derive(Serialize)]
struct ReservationResponse {
    success: bool,
    message: String,
}

/// Shared application state held across all request handlers.
struct AppState {
    db: DatabaseConnection,
}

fn now_millis() -> String {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis()
        .to_string()
}

/// Returns every inventory row from PostgreSQL.
async fn get_inventory(State(state): State<Arc<AppState>>) -> Result<Json<Vec<entity::Model>>, StatusCode> {
    let inventory = InventoryItem::find()
        .all(&state.db)
        .await
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    Ok(Json(inventory))
}

/// Returns a single inventory item by product ID. 404 if not found.
async fn get_inventory_item(
    State(state): State<Arc<AppState>>,
    Path(product_id): Path<String>,
) -> Result<Json<entity::Model>, StatusCode> {
    InventoryItem::find_by_id(&product_id)
        .one(&state.db)
        .await
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?
        .ok_or(StatusCode::NOT_FOUND)
        .map(Json)
}

/// Core helper: adjust quantity for a single product inside a DB transaction.
///
/// - Opens a new transaction
/// - Looks up the product by ID
/// - Optionally checks that enough stock exists (when `check_stock` is true)
/// - Applies `delta` (negative for reserve, positive for release)
/// - Commits on success, rolls back on failure
async fn adjust_quantity(
    db: &DatabaseConnection,
    product_id: &str,
    delta: i32,
    ok_message: &str,
    fail_message: &str,
    check_stock: bool,
) -> Result<Json<ReservationResponse>, StatusCode> {
    let txn = db.begin().await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let item_opt = InventoryItem::find_by_id(product_id)
        .one(&txn)
        .await
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    if let Some(item) = item_opt {
        if !check_stock || item.quantity_available >= delta {
            let mut active_item: entity::ActiveModel = item.into();
            active_item.quantity_available = Set(active_item.quantity_available.unwrap() + delta);
            active_item.update(&txn).await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
            txn.commit().await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
            return Ok(Json(ReservationResponse { success: true, message: ok_message.into() }));
        }
    }

    txn.rollback().await.ok();
    Ok(Json(ReservationResponse { success: false, message: fail_message.into() }))
}

/// Reserve stock for a single product (deduct quantity).
/// Called by the saga orchestrator for each item in an order.
async fn reserve_item(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<ReservationRequest>,
) -> Result<Json<ReservationResponse>, StatusCode> {
    adjust_quantity(
        &state.db,
        &payload.product_id,
        -payload.quantity,
        "Reserved",
        "Insufficient stock or item not found",
        true,
    ).await
}

/// Release stock for a single product (restore quantity).
/// Called by the orchestrator's compensating transaction on saga failure.
async fn release_item(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<ReservationRequest>,
) -> Result<Json<ReservationResponse>, StatusCode> {
    adjust_quantity(
        &state.db,
        &payload.product_id,
        payload.quantity,
        "Released",
        "Item not found",
        false,
    ).await
}

/// Reserve stock for multiple products in a single DB transaction.
///
/// Idempotent: if a product for this order was already reserved (detected
/// via the unique constraint on `(order_id, product_id)` in the reservations
/// table), it is silently skipped. This prevents double-reservation when
/// Kafka re-delivers an `order-created` event.
///
/// If any product has insufficient stock or is missing, the **entire
/// transaction is rolled back** — no partial reservations.
async fn batch_reserve(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<BatchReservationRequest>,
) -> Result<Json<ReservationResponse>, StatusCode> {
    let txn = state.db.begin().await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    for item in &payload.items {
        // Check if this order+product is already reserved (idempotency check)
        let existing = Reservation::find()
            .filter(reservation::Column::OrderId.eq(&payload.order_id))
            .filter(reservation::Column::ProductId.eq(&item.product_id))
            .one(&txn)
            .await
            .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

        if existing.is_some() {
            // Already reserved by a previous attempt — idempotent skip
            continue;
        }

        let product = InventoryItem::find_by_id(&item.product_id)
            .one(&txn)
            .await
            .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

        match product {
            Some(p) if p.quantity_available >= item.quantity => {
                // Insert a reservation row to mark this order+product as reserved
                let now = now_millis();
                let reservation_active = reservation::ActiveModel {
                    order_id: Set(payload.order_id.clone()),
                    product_id: Set(item.product_id.clone()),
                    quantity: Set(item.quantity),
                    created_at: Set(now),
                    ..Default::default()
                };
                reservation_active.insert(&txn).await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

                // Deduct stock
                let mut active: entity::ActiveModel = p.into();
                active.quantity_available = Set(active.quantity_available.unwrap() - item.quantity);
                active.update(&txn).await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
            }
            Some(_) => {
                txn.rollback().await.ok();
                return Ok(Json(ReservationResponse {
                    success: false,
                    message: format!("Insufficient stock for product: {}", item.product_id),
                }));
            }
            None => {
                txn.rollback().await.ok();
                return Ok(Json(ReservationResponse {
                    success: false,
                    message: format!("Product not found: {}", item.product_id),
                }));
            }
        }
    }

    txn.commit().await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Json(ReservationResponse { success: true, message: "All items reserved".into() }))
}

/// Release stock for multiple products in a single DB transaction.
///
/// Idempotent: only releases stock for reservations that still exist.
/// If a reservation was already released (or never created), it is
/// silently skipped. This is safe for compensating transactions that
/// may be called multiple times after a saga failure.
///
/// Unlike batch_reserve, this does NOT check stock levels — it simply
/// restores quantities. Missing products are silently skipped.
async fn batch_release(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<BatchReservationRequest>,
) -> Result<Json<ReservationResponse>, StatusCode> {
    let txn = state.db.begin().await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    for item in &payload.items {
        // Only release if a reservation exists (idempotent — skip if already released)
        let existing_reservation = Reservation::find()
            .filter(reservation::Column::OrderId.eq(&payload.order_id))
            .filter(reservation::Column::ProductId.eq(&item.product_id))
            .one(&txn)
            .await
            .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

        if let Some(reservation_row) = existing_reservation {
            // Restore stock
            let product = InventoryItem::find_by_id(&item.product_id)
                .one(&txn)
                .await
                .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

            if let Some(p) = product {
                let mut active: entity::ActiveModel = p.into();
                active.quantity_available = Set(active.quantity_available.unwrap() + item.quantity);
                active.update(&txn).await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
            }

            // Delete the reservation so the release is a no-op on retry
            reservation::Entity::delete(reservation::ActiveModel {
                id: Set(reservation_row.id),
                ..Default::default()
            })
            .exec(&txn)
            .await
            .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
        }
    }

    txn.commit().await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Json(ReservationResponse { success: true, message: "All items released".into() }))
}

/// CORS middleware — allows all origins, methods, and headers.
async fn cors(req: Request<Body>, next: Next) -> Response<Body> {
    if req.method() == Method::OPTIONS {
        let mut res = Response::new(Body::empty());
        *res.status_mut() = StatusCode::NO_CONTENT;
        let headers = res.headers_mut();
        headers.insert("access-control-allow-origin", HeaderValue::from_static("*"));
        headers.insert(
            "access-control-allow-methods",
            HeaderValue::from_static("GET, POST, PUT, PATCH, DELETE, OPTIONS"),
        );
        headers.insert("access-control-allow-headers", HeaderValue::from_static("*"));
        return res;
    }

    let mut res = next.run(req).await;
    let headers = res.headers_mut();
    headers.insert("access-control-allow-origin", HeaderValue::from_static("*"));
    headers.insert(
        "access-control-allow-methods",
        HeaderValue::from_static("GET, POST, PUT, PATCH, DELETE, OPTIONS"),
    );
    headers.insert("access-control-allow-headers", HeaderValue::from_static("*"));
    res
}

/// Application entry point.
///
/// 1. Reads `DATABASE_URL` (or uses default Docker Compose URL)
/// 2. Connects to PostgreSQL and runs pending migrations
/// 3. Starts the Axum HTTP server on port 8085
#[tokio::main]
async fn main() {
    let _ = dotenvy::dotenv();

    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://ecommerce:changeme@postgres:5432/ecommerce".to_string());

    let db = Database::connect(&database_url)
        .await
        .expect("Failed to connect to PostgreSQL");

    Migrator::up(&db, None)
        .await
        .expect("Failed to run database migrations");

    let shared_state = Arc::new(AppState { db });

    let app = Router::new()
        .route("/health", get(|| async { Json(serde_json::json!({"status": "ok", "service": "inventory"})) }))
        .route("/inventory", get(get_inventory))
        .route("/inventory/{product_id}", get(get_inventory_item))
        .route("/inventory/reserve", post(reserve_item))
        .route("/inventory/release", post(release_item))
        .route("/inventory/batch-reserve", post(batch_reserve))
        .route("/inventory/batch-release", post(batch_release))
        .layer(axum::middleware::from_fn(cors))
        .with_state(shared_state);

    let listener = TcpListener::bind("0.0.0.0:8085").await.unwrap();
    println!("Inventory service listening on 0.0.0.0:8085");
    axum::serve(listener, app).await.unwrap();
}
