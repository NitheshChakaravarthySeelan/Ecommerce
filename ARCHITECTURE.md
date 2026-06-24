# HLD / LLD Architecture Document

## High-Level Design (HLD)

### Goals
- Build a distributed ecommerce platform with secure auth, catalog, cart, ordering, inventory, payment, shipping, and saga orchestration.
- Use Kafka for event-driven communication and distributed transaction coordination.
- Support multi-language microservices: Java, Rust, Python.
- Provide a React/TypeScript frontend for customer and checkout flow.

### System Components
- Frontend: React/Next.js storefront
- API Gateway (future extension)
- Auth Service: Java Spring Boot, JWT auth
- Catalog Service: Rust, product data and search
- Cart Service: Python FastAPI, cart persistence
- Order Service: Python FastAPI, order lifecycle
- Inventory Service: Rust, stock reservation
- Payment Service: Python FastAPI, payment orchestration
- Shipping Service: Java Spring Boot, fulfillment
- Saga Orchestrator: Java Spring Boot, event-driven workflow
- Kafka: event bus
- PostgreSQL: transactional storage
- Redis: cache and state store

### Data Flow
1. Customer logs in and browses products.
2. Cart actions are sent to `cart-service`.
3. Order creation triggers `order-service` and starts saga orchestration.
4. Kafka topics carry events for inventory, payment, and shipping.
5. Saga orchestrator completes the order or compensates on failure.

## Low-Level Design (LLD)

### Auth Service
- Entities: `User`, `AuthRequest`, `AuthResponse`
- Endpoints:
  - `POST /auth/register`
  - `POST /auth/login`
  - `POST /auth/refresh`
  - `GET /auth/me`
- Implementation:
  - In-memory user store
  - JWT token generation
  - Secure password hashing placeholder

### Catalog Service
- Entities: `Product`, `Category`
- Endpoints:
  - `GET /products`
  - `GET /products/{id}`
  - `GET /categories`
- Implementation:
  - In-memory product catalog
  - JSON product APIs
  - Redis-ready caching design

### Cart Service
- Entities: `Cart`, `CartItem`
- Endpoints:
  - `GET /cart`
  - `POST /cart/items`
  - `PATCH /cart/items/{item_id}`
  - `DELETE /cart/items/{item_id}`
- Implementation:
  - In-memory cart state
  - Merge-on-login pattern
  - Expiration policies

### Order Service
- Entities: `Order`, `OrderItem`
- Endpoints:
  - `POST /orders`
  - `GET /orders`
  - `GET /orders/{id}`
- Implementation:
  - Order persistence stub
  - Saga start event emission
  - Status updates based on event results

### Inventory Service
- Events:
  - `inventory.reservation.requested`
  - `inventory.reserved`
  - `inventory.reservation.failed`
  - `inventory.released`
- Implementation:
  - Reserve and release stock atomically
  - Simple in-memory inventory ledger

### Payment Service
- Events:
  - `payment.requested`
  - `payment.succeeded`
  - `payment.failed`
- Implementation:
  - Payment validation stubs
  - Idempotent event handling pattern

### Shipping Service
- Entities: `Shipment`, `ShippingQuote`
- Endpoints:
  - `GET /shipping/quote`
  - `GET /shipping/{orderId}`
- Events:
  - `shipping.requested`
  - `shipping.scheduled`
  - `shipping.failed`
- Implementation:
  - Carrier quote generation
  - Shipment tracking scaffold

### Saga Orchestrator
- Saga steps:
  1. Reserve inventory
  2. Charge payment
  3. Schedule shipping
  4. Complete order
- Compensation:
  - Release inventory
  - Refund payment
  - Cancel shipping
- Implementation:
  - In-memory saga state storage
  - Event correlation using `sagaId`

## Deployment Design
- Each service runs in its own Docker container.
- Local environment uses Docker Compose.
- Production target uses Kubernetes with health probes and scaling.
- Observability via Prometheus, Grafana, and OpenTelemetry.

## Notes
This document is the starting point for implementing a production-grade ecommerce system. Each service can be extended with durable storage, Kafka consumers/producers, schema registry, and full transaction handling.
