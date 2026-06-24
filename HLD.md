# High-Level Design (HLD) - Ecommerce Microservices Platform

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      Frontend (Next.js)                          │
│                     (localhost:3000)                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway (Node.js)                         │
│  - Request routing to backend services                           │
│  - JWT authentication & validation                              │
│  - Trace ID propagation (x-trace-id header)                     │
│  - CORS & request logging                                       │
│                     (localhost:3001)                             │
└──────────────────────────────┬─────────────────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
    ┌────────────┐         ┌────────────┐        ┌────────────┐
    │  Sync APIs │         │ Event Bus  │        │ Auth Layer │
    │            │         │  (Kafka)   │        │   (JWT)    │
    └────────────┘         └────────────┘        └────────────┘
        │
        ├─────────────────────────────────────────────────┐
        │                                                  │
        ▼                                                  ▼
   ┌────────────────────┐                    ┌────────────────────┐
   │  Catalog Service   │                    │   Cart Service     │
   │  (Rust + Axum)     │                    │  (Python + Redis)  │
   │  - GET /products   │                    │  - GET/POST items  │
   │  - POST /products  │                    │  - PATCH quantity  │
   │  - PostgreSQL      │                    │  - DELETE items    │
   │  (Port: 8084)      │                    │  (Port: 8086)      │
   └────────────────────┘                    └────────────────────┘

   ┌────────────────────┐     ┌────────────────────┐
   │ Inventory Service  │     │  Auth Service      │
   │ (Rust + Axum)      │     │ (Java + Spring)    │
   │ - Track stock      │     │ - User login       │
   │ - Reserve items    │     │ - JWT generation   │
   │ - PostgreSQL       │     │ - PostgreSQL       │
   │ (Port: 8085)       │     │ (Port: 8081)       │
   └────────────────────┘     └────────────────────┘

   ┌────────────────────────────────────────────────────┐
   │          Orchestrator Service (Java)               │
   │  Event-driven Order Saga Orchestration             │
   │  - Listens to: order-created → payment-processed  │
   │  - Publishes: payment-initiated → order-completed │
   │  - PostgreSQL for saga state                       │
   │  (Port: 8089)                                      │
   └────────────────────────────────────────────────────┘

   ┌────────────────────┐     ┌──────────────────────┐
   │  Order Service     │     │ Payment Service      │
   │ (Python + FastAPI) │     │ (Python + FastAPI)   │
   │ - POST /orders     │     │ - POST /payments     │
   │ - GET /orders      │     │ - Processes payment  │
   │ - PostgreSQL       │     │ - PostgreSQL         │
   │ (Port: 8087)       │     │ (Port: 8088)         │
   └────────────────────┘     └──────────────────────┘

   ┌────────────────────┐     ┌──────────────────────┐
   │ Shipping Service   │     │   Saga Service       │
   │ (Java + Spring)    │     │ (Java + Spring)      │
   │ - Shipping quotes  │     │ - Saga orchestration │
   │ - Tracking         │     │ - PostgreSQL         │
   │ - PostgreSQL       │     │ (Port: 8083)         │
   │ (Port: 8082)       │     └──────────────────────┘
   └────────────────────┘
```

## Event Flow: Order Saga

### Happy Path

```
1. User places order (Cart → Order Service)
   └─ OrderService creates Order (PENDING)
   └─ Publishes: order-created

2. Orchestrator receives order-created
   └─ Initiates Payment
   └─ Publishes: payment-initiated

3. PaymentService processes payment
   └─ Calls payment gateway
   └─ Updates DB (SUCCESS/FAILED)
   └─ Publishes: payment-processed

4. Orchestrator receives payment-processed
   ├─ If SUCCESS:
   │  └─ Initiates Shipping
   │  └─ Publishes: shipping-initiated
   └─ If FAILED:
      └─ Publishes: saga-failed
      └─ Order marked FAILED

5. ShippingService processes shipment
   └─ Calculates shipping cost
   └─ Creates shipment record
   └─ Publishes: shipping-dispatched

6. Orchestrator receives shipping-dispatched
   └─ Marks Order as DELIVERED
   └─ Publishes: order-completed

Result: Order moves PENDING → PAYMENT_PROCESSED → SHIPPED → DELIVERED
```

### Error Path (Compensation)

```
If Payment fails:
  └─ Orchestrator publishes: saga-failed
  └─ Order Service receives saga-failed
  └─ Order status → FAILED
  └─ Cart remains (user can retry)
  └─ No compensation needed (payment not charged)

If Shipping fails:
  └─ Orchestrator publishes: saga-failed
  └─ OrderService receives saga-failed
  └─ Order status → FAILED
  └─ FUTURE: Trigger refund via PaymentService
  └─ FUTURE: Publish: refund-initiated
```

## Data Flow: Synchronous vs Asynchronous

### Synchronous (API Gateway → Service)
- **Catalog lookup**: GET /api/products → Catalog Service
- **Add to cart**: POST /api/cart/items → Cart Service
- **User auth**: POST /auth/login → Auth Service
- **Inventory check**: GET /api/inventory/:productId → Inventory Service

Response time: Direct (50-200ms)

### Asynchronous (Kafka Topics)
- **order-created**: Order Service → Orchestrator
- **payment-initiated**: Orchestrator → Payment Service
- **payment-processed**: Payment Service → Orchestrator
- **shipping-initiated**: Orchestrator → Shipping Service
- **shipping-dispatched**: Shipping Service → Orchestrator
- **order-completed**: Orchestrator → Order Service
- **saga-failed**: Orchestrator → Order Service (on error)

Response time: Eventually consistent (1-5 seconds)

## Key Infrastructure

### Kafka Topics (Event Bus)
```
order-created          → Published by: Order Service
payment-initiated      → Published by: Orchestrator
payment-processed      → Published by: Payment Service
shipping-initiated     → Published by: Orchestrator
shipping-dispatched    → Published by: Shipping Service
order-completed        → Published by: Orchestrator
saga-failed            → Published by: Orchestrator
```

### Authentication & Tracing
- **JWT Token**: 
  - Generated by Auth Service
  - Validated by API Gateway
  - Propagated to backend services via x-user-id header
  
- **Trace ID**:
  - Generated by API Gateway (x-trace-id header)
  - Propagated to all services
  - Logged in all services for debugging

### Database (PostgreSQL)
- **Catalog**: products table
- **Inventory**: inventory_items table
- **Orders**: orders table with status enum
- **Payments**: payments table
- **Shipping**: shipping_records table
- **Saga State**: saga_order_states table (for orchestration)

### Cache (Redis)
- **Cart Service**: User cart state
- Session storage
- Rate limiting buckets

## Deployment Flow

```
User Action
    ↓
Frontend (Next.js) - Browser
    ↓
API Gateway (Node.js:3001) - Validates JWT, logs trace ID
    ↓
Service (Rust/Python/Java) - Processes, stores in DB
    ↓
If Business Event (Order placed):
    ├─ Publish event to Kafka
    ├─ Orchestrator listens
    ├─ Initiates next step
    └─ Eventually consistent result
    ↓
Response flows back through API Gateway to Frontend
```

## Service Communication Matrix

| From | To | Method | Async |
|------|-----|--------|-------|
| Frontend | API Gateway | HTTP | No |
| API Gateway | Catalog | HTTP | No |
| API Gateway | Cart | HTTP | No |
| API Gateway | Order | HTTP | No |
| Order Service | Orchestrator | Kafka | Yes |
| Orchestrator | Payment Service | Kafka | Yes |
| Payment Service | Orchestrator | Kafka | Yes |
| Orchestrator | Shipping Service | Kafka | Yes |
| Shipping Service | Orchestrator | Kafka | Yes |
| Orchestrator | Order Service | Kafka | Yes |

## Fault Tolerance

1. **API Gateway Failure**: Frontend receives 503, user can retry
2. **Service Failure**: API Gateway returns 503 with trace ID for debugging
3. **Kafka Failure**: In-flight events queued, retry on broker recovery
4. **Payment Failure**: Saga stops, Order marked FAILED, user retries checkout
5. **Shipping Failure**: Saga stops, Order marked FAILED, compensation triggered

## Observability

### Logging
- All requests include trace ID (x-trace-id)
- Structured JSON logs with timestamp, service, message
- Error stack traces captured

### Monitoring
- API Gateway: Request count, latency, error rate
- Services: Processing time, database queries
- Kafka: Topic lag, consumer group lag

### Debugging
- Trace ID enables end-to-end flow tracking
- Each service logs with trace ID context
- Correlate logs across services using trace ID

---

## Next Phase (Post-MVP)

1. **Idempotency Keys**: Prevent duplicate processing
2. **Dead Letter Queues**: Handle failed events
3. **Circuit Breaker**: Prevent cascading failures
4. **Rate Limiting**: Per-user/per-IP limits at API Gateway
5. **Service Mesh (Istio)**: For advanced routing, retries, timeouts
6. **Canary Deployments**: Gradual rollout of new versions
