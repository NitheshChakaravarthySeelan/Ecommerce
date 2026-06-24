//! # Catalog Service (Rust/Axum)
//!
//! Product catalog — the source of truth for all product data including
//! name, description, price, category, and stock count.
//!
//! ## Endpoints
//! - `GET /products` — list all products (optional `?category=` filter)
//! - `GET /products/:id` — get a single product by ID
//! - `POST /products` — create a new product

mod entity;
mod migrator;

use axum::{
    body::Body,
    extract::{Path, Query, State},
    http::{HeaderValue, Method, Request, Response, StatusCode},
    middleware::Next,
    routing::get,
    Json, Router,
};
use crate::migrator::Migrator;
use entity::Entity as ProductEntity;
use sea_orm::{ActiveModelTrait, ColumnTrait, Database, DatabaseConnection, EntityTrait, QueryFilter, Set};
use sea_orm_migration::MigratorTrait;
use serde::Deserialize;
use std::sync::Arc;
use tokio::net::TcpListener;

/// Query parameters accepted by `GET /products`.
#[derive(Deserialize)]
struct ProductFilters {
    /// If set, only products matching this category are returned.
    category: Option<String>,
}

/// Request body for creating a new product.
#[derive(Deserialize)]
struct ProductCreate {
    name: String,
    description: String,
    category: String,
    image_url: String,
    price: f64,
    stock: i32,
    in_stock: bool,
}

/// Shared state holding the database connection pool.
struct AppState {
    db: DatabaseConnection,
}

/// List all products, optionally filtered by category.
///
/// # Note
/// No pagination — returns every matching product in one response.
/// For large catalogs, pagination should be added.
async fn list_products(
    State(state): State<Arc<AppState>>,
    Query(params): Query<ProductFilters>,
) -> Result<Json<Vec<entity::Model>>, StatusCode> {
    let mut query = ProductEntity::find();

    if let Some(category) = params.category {
        query = query.filter(entity::Column::Category.eq(category));
    }

    let products = query
        .all(&state.db)
        .await
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    Ok(Json(products))
}

/// Get a single product by its UUID.
///
/// Returns `404 Not Found` if the product ID does not exist.
async fn product_detail(
    State(state): State<Arc<AppState>>,
    Path(product_id): Path<String>,
) -> Result<Json<entity::Model>, StatusCode> {
    let product = ProductEntity::find_by_id(product_id)
        .one(&state.db)
        .await
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let product = product.ok_or(StatusCode::NOT_FOUND)?;
    Ok(Json(product))
}

/// Create a new product. A UUID is auto-generated as the product ID.
async fn create_product(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<ProductCreate>,
) -> Result<Json<entity::Model>, StatusCode> {
    let new_product = entity::ActiveModel {
        id: Set(uuid::Uuid::new_v4().to_string()),
        name: Set(payload.name),
        description: Set(payload.description),
        category: Set(payload.category),
        image_url: Set(payload.image_url),
        price: Set(payload.price),
        stock: Set(payload.stock),
        in_stock: Set(payload.in_stock),
    };

    let product = new_product
        .insert(&state.db)
        .await
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    Ok(Json(product))
}

/// Health check endpoint.
async fn health() -> Json<serde_json::Value> {
    Json(serde_json::json!({"status": "ok", "service": "catalog"}))
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
        .route("/health", get(health))
        .route("/products", get(list_products).post(create_product))
        .route("/products/:id", get(product_detail))
        .layer(axum::middleware::from_fn(cors))
        .with_state(shared_state);

    let listener = TcpListener::bind("0.0.0.0:8084").await.unwrap();
    println!("Catalog service listening on 0.0.0.0:8084");
    axum::serve(listener, app).await.unwrap();
}
