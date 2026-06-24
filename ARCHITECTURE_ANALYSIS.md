# Ecommerce Platform — Architecture Analysis

> Generated from full source code audit of all 10 microservices + 1 frontend.
> Date: 2026-06-18
> Last updated: 2026-06-20 — Resolved issues marked with ✅

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Complete API Endpoint Inventory](#2-complete-api-endpoint-inventory)
3. [Data Flow & Service Interaction](#3-data-flow--service-interaction)
4. [Bottlenecks, Scalability & Reliability](#4-bottlenecks-scalability--reliability)

---

## 1. System Overview

### Tech Stack

| Layer | Technology | Services |
|-------|-----------|----------|
| **Frontend** | Next.js 14 (TypeScript, Tailwind CSS, SWR) | `frontend` |
| **API Gateway** | Express.js + http-proxy + jsonwebtoken | `api-gateway` |
| **Auth** | Java 21, Spring Boot 3.3.3, JPA, PostgreSQL | `auth-java` |
| **Saga Orchestrator** | Java 21, Spring Boot 3.1.5, Kafka | `orchestrator-java` |
| **Saga State** | Java 21, Spring Boot 3.3.3, Kafka | `saga-java` |
| **Shipping** | Java 21, Spring Boot 3.3.3, Kafka, JPA | `shipping-java` |
| **Cart** | Python 3.12, FastAPI, Redis | `cart-python` |
| **Order** | Python 3.12, FastAPI, SQLAlchemy, Kafka | `order-python` |
| **Payment** | Python 3.12, FastAPI, SQLAlchemy, Kafka | `payment-python` |
| **Catalog** | Rust (Axum, SeaORM, PostgreSQL) | `catalog-rust` |
| **Inventory** | Rust (Axum, SeaORM, PostgreSQL) | `inventory-rust` |
| **Infrastructure** | PostgreSQL 15, Redis 7, Kafka 7.4.1, ZooKeeper 3.8.1 | docker-compose |

### Architecture Pattern

**Polyglot microservices + Orchestrator-based Saga pattern** for distributed transactions:

- Synchronous **REST** calls for CRUD (catalog, cart, auth, shipping status)
- Asynchronous **Kafka events** for the order fulfillment saga
- Single **API Gateway** as the sole entry point for all external traffic
- **Shared PostgreSQL** database (single instance, all services connect)

---

## 2. Complete API Endpoint Inventory

### 2.1 API Gateway (Express.js) — Entry Point

**File:** `services/api-gateway/src/index.js`

| Method | Path | Auth Required | Backend Service |
|--------|------|---------------|-----------------|
| GET | `/health` | Yes | Inline (gateway itself) |
| POST | `/auth/register` | **No** | Auth (8081) |
| POST | `/auth/login` | **No** | Auth (8081) |
| GET | `/api/products` | Yes | Catalog (8084) |
| POST | `/api/products` | Yes | Catalog (8084) |
| GET | `/api/products/:id` | Yes | Catalog (8084) |
| GET | `/api/inventory` | Yes | Inventory (8085) |
| POST | `/api/inventory/reserve` | Yes | Inventory (8085) |
| POST | `/api/inventory/release` | Yes | Inventory (8085) |
| GET | `/api/inventory/:productId` | Yes | Inventory (8085) — **MISSING in service** |
| GET | `/api/cart/:userId` | Yes | Cart (8086) |
| POST | `/api/cart/:userId/items` | Yes | Cart (8086) |
| GET | `/api/cart/:userId/items` | Yes | Cart (8086) |
| PATCH | `/api/cart/:userId/items/:itemId` | Yes | Cart (8086) |
| DELETE | `/api/cart/:userId/items/:itemId` | Yes | Cart (8086) |
| POST | `/api/orders` | Yes | Order (8087) |
| GET | `/api/orders` | Yes | Order (8087) |
| GET | `/api/orders/:orderId` | Yes | Order (8087) |
| POST | `/api/payments` | Yes | Payment (8088) |
| GET | `/api/payments/:paymentId` | Yes | Payment (8088) — **MISSING in service** |
| GET | `/api/shipping/quote` | Yes | Shipping (8082) |
| POST | `/api/shipping/:orderId` | Yes | Shipping (8082) |
| GET | `/api/shipping/:orderId` | Yes | Shipping (8082) |
| POST | `/api/saga/start` | Yes | Saga (8083) |
| GET | `/api/saga/:sagaId` | Yes | Saga (8083) |
| GET | `/api/health/auth` | Yes | Auth (8081) |
| GET | `/api/health/catalog` | Yes | Catalog (8084) |
| GET | `/api/health/inventory` | Yes | Inventory (8085) |
| GET | `/api/health/cart` | Yes | Cart (8086) |
| GET | `/api/health/orders` | Yes | Order (8087) |
| GET | `/api/health/payments` | Yes | Payment (8088) |
| GET | `/api/health/shipping` | Yes | Shipping (8082) |
| GET | `/api/health/orchestrator` | Yes | Orchestrator (8089) |
| GET | `/api/health/saga` | Yes | Saga (8083) |

**Global Middleware order:**
1. `cors()` — permissive (all origins)
2. `express.json()` — body parsing
3. Trace ID logging — generates/forwards `x-trace-id`
4. JWT authentication — skips `/auth/register` and `/auth/login`; returns 401 (no token) or 403 (invalid token)
5. Proxy handlers — forward to backend services with `x-trace-id` and `x-user-id` headers

### 2.2 Auth Service (Java Spring Boot)

**File:** `services/auth-java/.../AuthController.java`

| Method | Path | Handler | Logic |
|--------|------|---------|-------|
| GET | `/health` | `health()` | Returns `{"status": "ok", "service": "auth"}` |
| POST | `/auth/register` | `register(AuthRequest)` | Validates email uniqueness, hashes password (BCrypt), creates UserEntity with role "customer", returns JWT |
| POST | `/auth/login` | `login(AuthRequest)` | Looks up user by email, validates password hash, returns JWT |
| GET | `/auth/me` | `me(authorization)` | Parses Bearer token, validates JWT, returns user info (id, email, role). Falls back to anonymous/guest |

### 2.3 Catalog Service (Rust Axum)

**File:** `services/catalog-rust/src/main.rs`

| Method | Path | Handler | Logic |
|--------|------|---------|-------|
| GET | `/health` | Inline | Returns `{"status": "ok", "service": "catalog"}` |
| GET | `/products` | `list_products()` | Queries all products from PostgreSQL, optional `?category=` filter |
| POST | `/products` | `create_product()` | Creates a new product with generated UUID |
| GET | `/products/:id` | `product_detail()` | Queries single product by ID, 404 if not found |

### 2.4 Inventory Service (Rust Axum)

**File:** `services/inventory-rust/src/main.rs`

| Method | Path | Handler | Logic |
|--------|------|---------|-------|
| GET | `/health` | Inline | Returns `{"status": "ok", "service": "inventory"}` |
| GET | `/inventory` | `get_inventory()` | Returns all inventory items from PostgreSQL |
| POST | `/inventory/reserve` | `reserve_item()` | Reduces quantity in a DB transaction with stock check |
| POST | `/inventory/release` | `release_item()` | Increases quantity in a DB transaction (compensating action) |

**Note:** `GET /inventory/:productId` does **not** exist, despite the gateway proxying it at `/api/inventory/:productId`.

### 2.5 Cart Service (Python FastAPI + Redis)

**File:** `services/cart-python/main.py`

| Method | Path | Handler | Logic |
|--------|------|---------|-------|
| GET | `/health` | `health()` | Returns `{"status": "ok"}` |
| GET | `/cart/{user_id}` | `get_cart()` | Loads cart from Redis key `cart:{user_id}` |
| POST | `/cart/{user_id}/items` | `add_item()` | Upserts item in cart (updates qty if product exists) |
| GET | `/cart/{user_id}/items` | `get_items()` | Returns item list from cart |
| PATCH | `/cart/{user_id}/items/{item_id}` | `update_item()` | Updates quantity and price of specific item |
| DELETE | `/cart/{user_id}/items/{item_id}` | `delete_item()` | Removes item from cart |

### 2.6 Order Service (Python FastAPI + PostgreSQL + Kafka)

**File:** `services/order-python/main.py`

| Method | Path | Handler | Logic |
|--------|------|---------|-------|
| GET | `/health` | `health()` | Returns `{"status": "ok"}` |
| POST | `/orders` | `create_order()` | Generates UUID, saves order with status PENDING to PostgreSQL, publishes `order-created` to Kafka |
| GET | `/orders/{order_id}` | `get_order()` | Returns order details by ID |
| GET | `/orders` | `list_orders()` | Returns ALL orders (no pagination) |

### 2.7 Payment Service (Python FastAPI + PostgreSQL + Kafka)

**File:** `services/payment-python/main.py`

| Method | Path | Handler | Logic |
|--------|------|---------|-------|
| GET | `/health` | `health()` | Returns `{"status": "ok"}` |
| POST | `/payments` | `process_payment()` | Creates a payment record synchronously (REST fallback). Succeeds if amount > 0 |

**Note:** `GET /payments/{payment_id}` does **not** exist, despite the gateway proxying it.

### 2.8 Shipping Service (Java Spring Boot + Kafka)

**File:** `services/shipping-java/.../ShippingController.java`

| Method | Path | Handler | Logic |
|--------|------|---------|-------|
| GET | `/health` | `health()` | Returns `{"status": "ok", "service": "shipping"}` |
| GET | `/shipping/quote` | `quote(country)` | Returns hardcoded estimate (4 days, $12.50) |
| POST | `/shipping/{orderId}` | `createShipment()` | Creates/updates shipment record with status PENDING |
| GET | `/shipping/{orderId}` | `track()` | Returns shipment status and estimated delivery |

### 2.9 Saga Service (Java Spring Boot + Kafka)

**File:** `services/saga-java/.../SagaController.java`

| Method | Path | Handler | Logic |
|--------|------|---------|-------|
| GET | `/health` | `health()` | Returns `{"status": "ok", "service": "saga"}` |
| POST | `/saga/start` | `startSaga(payload)` | Generates UUID, persists state as "STARTED", publishes `saga-events` to Kafka |
| GET | `/saga/{sagaId}` | `status(sagaId)` | Returns saga status from DB |

### 2.10 Orchestrator Service (Java Spring Boot + Kafka)

**File:** `services/orchestrator-java/.../HealthController.java`

| Method | Path | Handler | Logic |
|--------|------|---------|-------|
| GET | `/health` | `health()` | Returns `{"status": "ok"}` |

The orchestrator has **no REST endpoints** besides health — it is purely event-driven via Kafka listeners.

### 2.11 Frontend API Client (TypeScript)

**File:** `frontend/src/lib/api.ts`

The frontend defines base URLs for each service pointing to the API Gateway at `http://localhost:3001`. All communication goes through `api-gateway`.

---

## 3. Data Flow & Service Interaction

### 3.1 Normal Flow: User Places an Order

```
Frontend                  API Gateway            Services                        Kafka Topics
─────────                ───────────            ────────                        ────────────
   │                         │                     │                                │
   │── POST /api/orders ────►│──► JWT Auth ──► Order Service                        │
   │                         │                     │                                │
   │                         │                     │── Save order (status=PENDING)  │
   │                         │                     │── Publish ────────────────────►│ order-created
   │                         │                     │                                │
   │                         │                     │◄── Kafka consumer ─────────────│ order-completed
   │◄── Response ────────────│◄── Response         │    (background)                │ saga-failed
```

### 3.2 Saga Flow (Orchestrator-Driven)

```
 order-created topic
       │
       ▼
┌───────────────────────────────────────────────────┐
│ Orchestrator: handleOrderCreated()                │
│  1. fetchOrderItems() ──REST──► Order Service     │
│  2. reserveInventory() ──REST──► Inventory Service│
│     (one HTTP call per item, sequentially)         │
│  3. Publish ──► payment-initiated topic           │
│  On failure: failSaga() → releaseInventory +      │
│               publish saga-failed                  │
└───────────────────────────────────────────────────┘
                          │
                          ▼
            payment-initiated topic
                          │
                          ▼
┌───────────────────────────────────────────────────┐
│ Payment Service: handle_payment_initiated()       │
│  1. If amount > 0 → status = "SUCCEEDED"          │
│     else → status = "FAILED"                       │
│  2. Save payment record to PostgreSQL              │
│  3. Publish ──► payment-processed topic           │
└───────────────────────────────────────────────────┘
                          │
                          ▼
            payment-processed topic
                          │
                          ▼
┌───────────────────────────────────────────────────┐
│ Orchestrator: handlePaymentProcessed()            │
│  1. If FAILED → failSaga()                        │
│  2. If SUCCEEDED → Publish ──► shipping-initiated │
└───────────────────────────────────────────────────┘
                          │
                          ▼
            shipping-initiated topic
                          │
                          ▼
┌───────────────────────────────────────────────────┐
│ Shipping Service: handleShippingInitiated()       │
│  1. Create/update ShippingEntity (status=SHIPPED) │
│  2. Publish ──► shipping-dispatched topic         │
└───────────────────────────────────────────────────┘
                          │
                          ▼
            shipping-dispatched topic
                          │
                          ▼
┌───────────────────────────────────────────────────┐
│ Orchestrator: handleShippingDispatched()          │
│  1. Publish ──► order-completed topic             │
│  2. Update SagaState in DB → COMPLETED            │
└───────────────────────────────────────────────────┘
                          │
                          ▼
             order-completed topic
                          │
                          ▼
┌───────────────────────────────────────────────────┐
│ Order Service: handle_order_completed()           │
│  Consumer on order-completed & saga-failed        │
│  1. Update order status → DELIVERED / FAILED      │
└───────────────────────────────────────────────────┘
```

### 3.3 Compensating Transaction (Failure) Flow

```
 Any saga step fails
       │
       ▼
┌───────────────────────────────────────────────────┐
│ Orchestrator: failSaga()                          │
│  1. releaseInventory() ──REST► Inventory Service  │
│     (releases ALL items reserved for this order)   │
│  2. Publish ──► saga-failed topic                 │
│  3. Update SagaState → FAILED, completed=true     │
└───────────────────────────────────────────────────┘
       │
       ▼
  saga-failed topic
       │
       ▼
┌───────────────────────────────────────────────────┐
│ Order Service: marks order as "FAILED"            │
└───────────────────────────────────────────────────┘
```

### 3.4 Data Domains & Persistence

| Service | Data Stored | Storage | Schema |
|---------|------------|---------|--------|
| Auth | Users (id, email, password_hash, role) | PostgreSQL | `users` table (auth-java schema) |
| Catalog | Products (id, name, desc, category, price, stock) | PostgreSQL | `products` table |
| Inventory | Inventory items (product_id, quantity_available) | PostgreSQL | `inventory_items` table |
| Cart | Cart (cart_id, user_id, items[]) | Redis | Key-value: `cart:{user_id}` → JSON |
| Order | Orders (order_id, user_id, status, total, items) | PostgreSQL | `orders` table |
| Payment | Payments (payment_id, order_id, amount, method, status) | PostgreSQL | `payments` table |
| Shipping | Shipments (order_id, status, estimated_delivery) | PostgreSQL | `shipping` table |
| Saga (orchestrator) | Saga state (order_id, status, step, version, retry_count) | PostgreSQL | `saga_states` table |
| Saga (saga service) | Saga state (saga_id, status, payload) | PostgreSQL | `saga_events` table |

**Critical:** All services share the **same single PostgreSQL instance** (`postgres:5432/ecommerce`). There is no schema or database isolation per service.

### 3.5 Kafka Topics

| Topic | Producer | Consumer(s) | Payload |
|-------|----------|-------------|---------|
| `order-created` | Order service | Orchestrator | `{orderId, userId, totalAmount, traceId, timestamp}` |
| `payment-initiated` | Orchestrator | Payment service | `{orderId, userId, amount, traceId, timestamp}` |
| `payment-processed` | Payment service | Orchestrator | `{paymentId, orderId, amount, status, traceId, timestamp}` |
| `shipping-initiated` | Orchestrator | Shipping service | `{orderId, traceId, timestamp}` |
| `shipping-dispatched` | Shipping service | Orchestrator | `{orderId, trackingId, traceId, timestamp}` |
| `order-completed` | Orchestrator | Order service | `{orderId, status, traceId, timestamp}` |
| `saga-failed` | Orchestrator | Order service | `{orderId, reason, traceId, timestamp}` |
| `saga-events` | Saga service | (none visible) | `{sagaId, type, payload}` |

---

## 4. Bottlenecks, Scalability & Reliability

### 4.1 Critical Issues

#### 🔴 CRITICAL: Shared Single PostgreSQL Instance

**All 8 services** that persist data connect to the **same PostgreSQL 15** instance with the same credentials (`ecommerce:changeme`).

- **Impact:** No connection pool isolation — a runaway query in one service starves all others. No read replicas for catalog queries. Single point of failure for the entire platform. Violates the microservices principle of "database per service."
- **Fix:** Separate database instances (or at minimum separate schemas with per-service connection pools). Use PgBouncer for connection pooling.

#### ~~🔴 CRITICAL: Synchronous REST Calls Inside Kafka Listeners~~ ✅ RESOLVED

**Status: Fixed** — Saga processing is now offloaded from Kafka consumer threads to a dedicated `sagaTaskExecutor` thread pool (`AsyncConfig.java`). The three `@KafkaListener` methods now perform only quick idempotency checks on the consumer thread and submit the heavy processing via `CompletableFuture.supplyAsync(task, sagaTaskExecutor)`. The returned `CompletableFuture<Void>` tells Spring Kafka to hold the offset commit until processing completes.

**What changed:**
- `AsyncConfig.java` — new `ThreadPoolTaskExecutor` bean (4 core / 8 max threads, `saga-worker-` prefix)
- `OrderSagaOrchestrator.java` — listeners return `CompletableFuture<Void>`, processing extracted into package-private `process*()` methods
- Consumer threads are freed immediately to poll more messages
- Offsets are still committed only after successful processing (future-based back-pressure)

#### ~~🔴 CRITICAL: Per-Item Inventory Reservation (N+1 Problem)~~ ✅ RESOLVED

**Status: Fixed** — Added `POST /inventory/batch-reserve` and `POST /inventory/batch-release` endpoints to the Rust inventory service. All items in an order are now reserved/released in a **single HTTP call** and a **single DB transaction**.

**What changed:**
- `inventory-rust/src/main.rs` — new `batch_reserve()` and `batch_release()` handlers that iterate items inside one `txn`, rolling back the entire batch if any item has insufficient stock
- `OrderSagaOrchestrator.java` — `batchReserveInventory()` replaces `reserveInventory()`, using `Map.of("order_id", orderId, "items", items)` with all items in one request
- `OrderSagaOrchestrator.java` — `batchReleaseInventory()` replaces per-item release loop
- `api-gateway/src/index.js` — routes added for `/api/inventory/batch-reserve` and `/api/inventory/batch-release`

#### ~~🔴 CRITICAL: No Idempotency on Kafka Event Handlers~~ ✅ RESOLVED

**Status: Fixed** — Inventory reservation is now idempotent via a unique constraint on `(order_id, product_id)` in a new `reservations` table. The orchestrator's `acquireSaga()` properly handles `DataIntegrityViolationException` from the unique constraint on `saga_states.orderId`. Kafka producer is configured with `enable.idempotence=true` and `acks=all` to prevent duplicate downstream events.

**What changed:**
- `inventory-rust/src/migrator.rs` — new `reservations` table with unique index `idx_reservations_order_product_unique`
- `inventory-rust/src/reservation.rs` — new SeaORM entity for the reservations table
- `inventory-rust/src/main.rs` — `batch_reserve` checks for existing reservations before decrementing stock; `batch_release` deletes reservations after restoring stock. Both are idempotent.
- `orchestrator-java/.../OrderSagaOrchestrator.java` — `acquireSaga()` now catches `DataIntegrityViolationException` (unique constraint safety net)
- `orchestrator-java/src/main/resources/application.yml` — `enable.idempotence: true`, `acks: all`, `max.in.flight.requests.per.connection: 5`, `isolation.level: read_committed`

#### 🔴 CRITICAL: No Dead Letter Queue (DLQ) for Kafka

Every Kafka consumer that encounters an error either:
- **Silently swallows it** (ShippingSagaListener.java:55-57): `log.error(...)` but does NOT rethrow. The offset is committed, the event is lost forever.
- **Calls failSaga()** (Orchestrator): But if the failSaga() itself fails (e.g., inventory service is down for release), the exception propagates and the event is re-delivered — causing an infinite retry loop.

- **Impact:** Events can be silently dropped or infinitely retried with no observability.
- **Fix:** Configure ` suicide.on.exception` or use a Dead Letter Topic. Implement `CommonContainerStopperAfterMaxRetries` or manual DLQ publishing.

#### 🔴 CRITICAL: Synchronous SQLAlchemy in Async FastAPI (Order & Payment)

```python
# order-python/main.py:160-163
with SessionLocal() as session:
    session.add(order_db)
    session.commit()
```

- **Impact:** FastAPI async endpoints call **synchronous** SQLAlchemy operations, blocking the asyncio event loop. Under load, all concurrent requests are serialized. This negates the benefits of async I/O.
- **Fix:** Use `AsyncSession` from `sqlalchemy.ext.asyncio` with `async with session.begin()`.

#### ~~🔴 CRITICAL: Hardcoded JWT Secret Fallbacks~~ ✅ RESOLVED

**Both the gateway and auth service now fail at startup if `JWT_SECRET` is not set or is under 32 characters.**
- `JwtConfig.java`: removed `@Value` default, added `@PostConstruct` validation (≥32 chars check)
- `api-gateway/src/index.js`: removed `||` fallback, `process.exit(1)` if secret is missing/short
- `application.properties`: removed `:dev-secret-key-...` default from `${JWT_SECRET}`

#### 🔴 CRITICAL: No Authentication on Inter-Service Calls

The orchestrator calls order and inventory services directly with NO authentication headers:

```java
// OrderSagaOrchestrator.java:43
ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
```

- **Impact:** Any service that can reach the internal Docker network can call these endpoints without authentication.
- **Fix:** Use mTLS, internal JWT tokens, or a service mesh (Istio/Linkerd).

### 4.2 High Priority Issues

#### ~~🟠 HIGH: Missing API Endpoints (Gateway/Service Mismatch)~~ ✅ RESOLVED

| Gateway Route | Backend Service | Status |
|--------------|----------------|--------|
| `GET /api/inventory/:productId` | Inventory (Rust) | ✅ Added `get_inventory_item()` handler + route |
| `GET /api/payments/:paymentId` | Payment (Python) | ✅ Added `get_payment()` endpoint with 404 handling |

The gateway routes already existed — the backend implementations were missing.

#### ~~🟠 HIGH: No Pagination on List Endpoints~~ ✅ RESOLVED

**All list endpoints now support `limit` and `offset` query parameters:**
- `GET /orders?limit=20&offset=0` — Order service (Python), max limit=100
- `GET /inventory?limit=20&offset=0` — Inventory service (Rust), max limit=100
- `GET /products?limit=20&offset=0` — Catalog service (Rust), max limit=100

All return `{data: [...], total: N, limit: N, offset: N}` paginated response bodies.

#### 🟠 HIGH: Order-created Published Before Event Confirmation

```python
# order-python/main.py:160-164
with SessionLocal() as session:
    session.add(order_db)
    session.commit()       # DB save succeeds
await publish_order_created(order, trace_id)  # Kafka may fail
```

If Kafka is unavailable, the order is saved to DB but `order-created` is never published. The order becomes an **orphan** — stuck in PENDING forever with no saga to process it.

**Fix:** Use an outbox pattern: save event to DB in the same transaction, then have a separate relay process publish to Kafka.

#### ~~🟠 HIGH: No Saga Timeout~~ ✅ RESOLVED

**A scheduled sweep task (`SagaTimeoutTask`) runs every 60 seconds** and fails sagas that have been `IN_PROGRESS` without update for longer than `saga.timeout-minutes` (default: 30). The task:
- Queries `saga_states` where `completed=false` AND `status='IN_PROGRESS'` AND `lastUpdatedAt < cutoff`
- Marks them `FAILED` with `currentStep='timed-out'`
- Publishes `saga-failed` event so the order service marks the order as failed
- Added `createdAt` and `lastUpdatedAt` timestamp fields to `SagaState` entity
- Requires `@EnableScheduling` on `OrchestratorApplication`

#### 🟠 HIGH: No Rate Limiting or Circuit Breaking

The API gateway has:
- ❌ No rate limiting
- ❌ No circuit breaker
- ❌ No request queuing
- ❌ No retry logic on proxy failures

A single abusive client can DoS the entire platform. A downstream service failure causes the gateway to return 503 with no graceful degradation.

#### 🟠 HIGH: Cart Data Volatility (Redis Only)

Cart data is persisted **only in Redis** with no RDB/AOF persistence or replication configured. If Redis crashes or restarts, all user carts are lost. There's no fallback to PostgreSQL.

#### ~~🟠 HIGH: Spring Boot Version Mismatch~~ ✅ RESOLVED

**All services now use Spring Boot 3.3.3.** The orchestrator's `pom.xml` parent was updated from `3.1.5` → `3.3.3`.

#### ~~🟠 HIGH: Global CORS (Allow-Origin: *)~~ ✅ RESOLVED

**All services now respect `CORS_ALLOWED_ORIGINS` env var (defaults to `*` for dev).**
- Java services: `CorsConfig.java` reads `${cors.allowed-origins}` from env and splits on comma
- Python services: `os.getenv("CORS_ALLOWED_ORIGINS", "*").split(",")`

#### ~~🟠 HIGH: Orchestrator Duplicate Dependency Import~~ ✅ RESOLVED

**Clean.** `OrderSagaOrchestrator.java` now has only a single `import org.slf4j.MDC;` (already cleaned up in prior edits).

### 4.3 Medium Priority Issues

#### ~~🟡 MEDIUM: No Connection Pool Limits~~ ✅ RESOLVED

**All services now have explicit pool limits:**
- Java services: `spring.datasource.hikari.maximum-pool-size=${DB_POOL_MAX:10}`, min-idle=2, timeout=5s
- Python services (order, payment): `pool_size=5`, `max_overflow=5`, `pool_pre_ping=True`
- All configurable via environment variables (`DB_POOL_MAX`, `DB_POOL_SIZE`, etc.)

#### ~~🟡 MEDIUM: Hardcoded Service URLs in Orchestrator~~ ✅ RESOLVED

**Service URLs are now configurable via `@Value("${inventory.url:...}")` and `@Value("${order.url:...}")`.** Defaults match the original hardcoded values for backward compatibility. Can be overridden via `inventory.url` and `order.url` environment variables.

#### ~~🟡 MEDIUM: No Graceful Shutdown in Python Services~~ ✅ RESOLVED

**Order and payment services now use `asyncio.Event` + `asyncio.wait_for` for graceful shutdown:**
- Kafka consumers poll with a 1-second timeout and exit when `shutdown_event` is set
- Lifespan `finally` block cancels the background consumer task and stops the producer
- Cart service (Redis-only) shuts down cleanly via uvicorn's default lifespan handling

#### ~~🟡 MEDIUM: HTTP 500 vs Meaningful Error Responses~~ ✅ RESOLVED

**Order and payment services now have a global exception handler** that returns `{"error": "Internal server error", "traceId": "..."}` with structured JSON and trace ID instead of raw 500 responses. Gateway's `createProxyHandler` already returns 503 with traceId on proxy errors.

#### ~~🟡 MEDIUM: Shipping Quote Is Hardcoded~~ ✅ RESOLVED

**Shipping quote values are now configurable via env vars:**
- `shipping.estimated-days` (default: 4)
- `shipping.cost` (default: 12.50)

The endpoint remains a stub but the business values can be tuned without a code change.

#### ~~🟡 MEDIUM: No Endpoint Auth for `/health` Routes~~ ✅ RESOLVED

**Health check routes no longer require JWT.** The gateway's `authenticateToken` middleware now skips `/health` and `/api/health/*` paths.

#### 🟡 MEDIUM: Single Kafka Broker

The compose file uses `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1` with a single broker. If the Kafka container goes down, all event processing halts and message loss is possible.

#### 🟡 MEDIUM: Weak Database Credentials

`ecommerce:changeme` is used across all services and in Docker Compose, source files, and Dockerfiles. This is fine for development but must be called out for production.

### 4.4 Scalability Assessment

| Service | Language | Scaling Strategy | Bottleneck |
|---------|----------|-----------------|------------|
| API Gateway | Node.js | Horizontal (stateless) | Single-threaded event loop. Consider `cluster` module or PM2 |
| Auth | Java | Horizontal (stateless) | DB connection pool. Needs read replicas for `auth/me` lookups |
| Catalog | Rust | Horizontal (stateless) | DB queries. Add pagination, caching (Redis), read replicas |
| Inventory | Rust | Horizontal (stateless) | DB transactions (row-level locks on reserve). Consider partitioning |
| Cart | Python | Horizontal (stateless) | Redis. Add Redis Cluster/Sentinel for HA. Async is fine |
| Order | Python | Horizontal (stateless) | **Sync DB in async handler** — serializes event loop |
| Payment | Python | Horizontal (stateless) | Same sync DB issue. Amount validation is trivial |
| Shipping | Java | Horizontal (stateless) | DB writes. Kafka consumer group auto-rebalances |
| Saga/Orchestrator | Java | Horizontal (stateless) | **Synchronous REST in Kafka listener** — primary bottleneck |

**Overall Scaling Ceiling:** The **shared single PostgreSQL instance** is the ultimate bottleneck. No amount of service replicas will help if the database cannot handle the connection + query load.

### 4.5 Reliability Assessment

| Concern | Current State | Recommendation |
|---------|--------------|---------------|
| **SPOF** | Single Postgres, single Kafka, single Redis | HA configurations: Postgres replication, Kafka cluster (3+ brokers), Redis Sentinel |
| **Data Loss** | No Kafka replication, no Redis persistence | Enable Kafka `min.insync.replicas`, enable Redis AOF |
| **Orphan Orders** | Kafka pub after DB commit | Outbox pattern: write event + DB in same transaction |
| **Duplicate Events** | ✅ Inventory reservations are idempotent (unique constraint), `acquireSaga` handles `DataIntegrityViolationException`, Kafka producer is idempotent | Idempotency keys on payment and shipping handlers (future work) |
| **Event Loss** | No DLQ, silent catch blocks | DLQ for all consumer groups, never swallow exceptions silently |
| **Crash Recovery** | Saga state stored but no timeout | Periodic sweep job to detect and fail stuck sagas |
| **Auth Bypass** | Internal calls lack auth | mTLS or internal JWT for service-to-service |
| **Monitoring** | Basic trace ID logging only | Add metrics (Prometheus), distributed tracing (OpenTelemetry), structured audit logs |
| **Health Checks** | Container-level only (`depends_on`) | Add Docker HEALTHCHECK + readiness probes to all services |

---

## Summary

This platform demonstrates a well-structured **polyglot microservices architecture** with a clear **orchestrator-based saga pattern**. The service boundaries are logical, the Kafka event flow is well-defined, and the use of Rust for high-throughput services (catalog, inventory) is appropriate.

**However, it has significant production-readiness gaps:**

1. **Shared PostgreSQL** is the single biggest risk — it couples all services and is a total SPOF
2. ~~Synchronous HTTP in Kafka listeners~~ ✅ **Resolved** — async processing with `sagaTaskExecutor`
3. ~~No idempotency~~ ✅ **Resolved** — inventory `reservations` table + `DataIntegrityViolationException` handling + idempotent Kafka producer
4. **Sync DB operations in async Python** kills concurrency in order/payment services
5. **No DLQ, no retry mechanism** means event loss under failure is still probable

**Top 5 fixes for production readiness:**
1. Separate databases (or schemas + connection pools) per service
2. ✅ ~~Async HTTP (WebClient) in the orchestrator + batch inventory reservation~~ — **Done** (batch endpoints + async executor)
3. ✅ ~~Idempotency on event handlers~~ — **Done** (unique constraint on reservations, integrity violation handling, idempotent producer)
4. DLQ configuration for all consumer groups (still missing)
5. Async SQLAlchemy in order and payment services
6. Outbox pattern for order creation to prevent orphan orders
