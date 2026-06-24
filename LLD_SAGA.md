# Low-Level Design (LLD) - Order Saga Orchestration

## Overview
This document details the Order Saga implementation using the Orchestration pattern with Kafka for event-driven coordination between Order, Payment, and Shipping services.

## State Diagram

```
                    ┌──────────────┐
                    │ Order Created│
                    └──────┬───────┘
                           │ [OrderCreatedEvent]
                           ▼
                  ┌──────────────────┐
                  │ PAYMENT_PENDING  │
                  └──────┬───────────┘
                         │ [PaymentInitiatedEvent sent]
                         │
         ┌───────────────┴────────────────┐
         │                                │
         ▼ (Payment SUCCESS)              ▼ (Payment FAILED)
   ┌──────────────────┐              ┌──────────────┐
   │ PAYMENT_SUCCESS  │              │ FAILED       │
   └────────┬─────────┘              └──────────────┘
            │ [ShippingInitiatedEvent sent]
            │
            ▼
   ┌──────────────────┐
   │ SHIPPING_PENDING │
   └────────┬─────────┘
            │ [ShippingDispatchedEvent]
            │
            ▼
   ┌──────────────────┐
   │ DELIVERED        │
   └──────────────────┘
```

## Event Schema

### 1. OrderCreatedEvent
Published by: **Order Service**
Topic: `order-created`

```json
{
  "orderId": "order-12345",
  "userId": "user-789",
  "totalAmount": 199.99,
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1686830400000,
  "items": [
    {
      "productId": "p1",
      "quantity": 2,
      "price": 34.99
    }
  ]
}
```

### 2. PaymentInitiatedEvent
Published by: **Orchestrator**
Topic: `payment-initiated`

```json
{
  "orderId": "order-12345",
  "userId": "user-789",
  "amount": 199.99,
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1686830400100
}
```

### 3. PaymentProcessedEvent
Published by: **Payment Service**
Topic: `payment-processed`

```json
{
  "paymentId": "pay-456",
  "orderId": "order-12345",
  "status": "SUCCESS",
  "transactionId": "txn-789",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1686830400500,
  "failureReason": null
}
```

### 4. ShippingInitiatedEvent
Published by: **Orchestrator**
Topic: `shipping-initiated`

```json
{
  "orderId": "order-12345",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1686830400600
}
```

### 5. ShippingDispatchedEvent
Published by: **Shipping Service**
Topic: `shipping-dispatched`

```json
{
  "orderId": "order-12345",
  "trackingId": "track-999",
  "carrier": "FedEx",
  "estimatedDelivery": "2024-06-20",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1686830400800
}
```

### 6. OrderCompletedEvent
Published by: **Orchestrator**
Topic: `order-completed`

```json
{
  "orderId": "order-12345",
  "status": "DELIVERED",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1686830401000
}
```

### 7. SagaFailedEvent
Published by: **Orchestrator**
Topic: `saga-failed`

```json
{
  "orderId": "order-12345",
  "reason": "Payment declined",
  "failedStep": "PAYMENT",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1686830400700
}
```

## Orchestrator Implementation

### Class: OrderSagaOrchestrator

```
Package: com.ecommerce.orchestrator.service
```

#### Method 1: handleOrderCreated()
```
Trigger: @KafkaListener(topics = "order-created")
Logic:
  1. Extract orderId, userId, totalAmount from event
  2. Log saga start with traceId
  3. Create PaymentInitiatedEvent
  4. Send to kafka topic: payment-initiated
  5. On error: publish SagaFailedEvent
Idempotency: orderId as key ensures single processing per order
Timeout: No timeout (event-driven, fire-and-forget)
```

#### Method 2: handlePaymentProcessed()
```
Trigger: @KafkaListener(topics = "payment-processed")
Logic:
  1. Extract orderId, status from event
  2. If status != SUCCESS:
     a. Log error
     b. Publish SagaFailedEvent
     c. Return (no further steps)
  3. Create ShippingInitiatedEvent
  4. Send to kafka topic: shipping-initiated
  5. On error: publish SagaFailedEvent
Idempotency: paymentId as key prevents duplicate processing
Timeout: No timeout (event-driven)
```

#### Method 3: handleShippingDispatched()
```
Trigger: @KafkaListener(topics = "shipping-dispatched")
Logic:
  1. Extract orderId, trackingId from event
  2. Create OrderCompletedEvent
  3. Send to kafka topic: order-completed
  4. Log saga completion with traceId
  5. On error: publish SagaFailedEvent
Idempotency: orderId as key ensures single completion per order
Timeout: No timeout (event-driven)
```

#### Method 4: publishSagaFailed()
```
Usage: Called on exception in any step
Logic:
  1. Create SagaFailedEvent with reason
  2. Send to kafka topic: saga-failed
  3. Log error with orderId, traceId, reason
  4. Kafka partition: orderId (ensures ordering)
```

## Service Implementations

### Order Service
**File**: `services/order-python/main.py`

#### Endpoint: POST /orders
```
Input:
  {
    "userId": "user-789",
    "items": [{"product_id": "p1", "quantity": 2}],
    "total_amount": 199.99
  }

Logic:
  1. Create Order record with status = PENDING
  2. Save to PostgreSQL
  3. Publish OrderCreatedEvent to Kafka
  4. Return Order object

Output:
  {
    "order_id": "order-12345",
    "status": "PENDING",
    "user_id": "user-789",
    "total_amount": 199.99
  }
```

#### Listener: saga-failed topic
```
On SagaFailedEvent:
  1. Extract orderId from event
  2. Update order.status = FAILED
  3. Log failure reason
  4. Notify user (future: send email)
```

#### Listener: order-completed topic
```
On OrderCompletedEvent:
  1. Extract orderId from event
  2. Update order.status = DELIVERED
  3. Update delivery_date = now()
  4. Log completion with traceId
```

### Payment Service
**File**: `services/payment-python/main.py`

#### Listener: payment-initiated topic
```
On PaymentInitiatedEvent:
  1. Extract orderId, userId, amount from event
  2. Call payment gateway (Stripe/PayPal simulation)
  3. Create Payment record with status
  4. If SUCCESS:
     a. Save transaction_id
     b. Publish PaymentProcessedEvent (status=SUCCESS)
  5. If FAILED:
     a. Save failure_reason
     b. Publish PaymentProcessedEvent (status=FAILED)
  6. Always log with traceId
```

### Shipping Service
**File**: `services/shipping-java/src/main/java/...`

#### Listener: shipping-initiated topic
```
On ShippingInitiatedEvent:
  1. Extract orderId from event
  2. Query order details via Order Service (HTTP)
  3. Calculate shipping cost & time
  4. Create ShippingRecord
  5. Generate tracking ID
  6. Publish ShippingDispatchedEvent
  7. Log with traceId
```

## Error Handling & Retry Logic

### Transient Errors (Retry)
```
Scenario: Kafka broker temporarily down
Handling:
  1. Orchestrator stores event in local queue
  2. Kafka producer has built-in retry (exponential backoff)
  3. After max retries (3), log error and move to DLQ
  4. DLQ consumer alerts ops team
```

### Idempotency
```
Problem: Kafka at-least-once delivery, might send duplicate events
Solution: 
  1. Each listener checks if event already processed
  2. Use (orderId, eventType) as idempotency key in DB
  3. If duplicate: skip processing, return success
  4. Query: SELECT saga_state WHERE order_id=? AND step=?
```

### Compensation
```
Scenario: Payment succeeds, but Shipping fails
Current: Order marked FAILED, user retries from checkout
Future: 
  1. Publish RefundInitiatedEvent
  2. Payment Service refunds amount
  3. Update Order.status = REFUNDED
```

## Database Schema (Saga State)

### Orchestrator: saga_order_states table
```sql
CREATE TABLE saga_order_states (
  id UUID PRIMARY KEY,
  order_id VARCHAR(255) UNIQUE NOT NULL,
  current_step VARCHAR(50) NOT NULL,
  status VARCHAR(50) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  trace_id VARCHAR(255),
  failure_reason TEXT,
  CONSTRAINT steps_check CHECK (
    current_step IN ('PAYMENT_PENDING', 'PAYMENT_SUCCESS', 'SHIPPING_PENDING', 'DELIVERED', 'FAILED')
  )
);
```

### Order Service: orders table
```sql
CREATE TABLE orders (
  id VARCHAR(255) PRIMARY KEY,
  user_id VARCHAR(255) NOT NULL,
  status VARCHAR(50) NOT NULL,
  total_amount DECIMAL(10, 2),
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  CONSTRAINT order_status_check CHECK (
    status IN ('PENDING', 'PAYMENT_PROCESSED', 'SHIPPED', 'DELIVERED', 'FAILED', 'REFUNDED')
  )
);

CREATE TABLE order_items (
  id UUID PRIMARY KEY,
  order_id VARCHAR(255) NOT NULL REFERENCES orders(id),
  product_id VARCHAR(255),
  quantity INT,
  unit_price DECIMAL(10, 2),
  CONSTRAINT qty_check CHECK (quantity > 0)
);
```

### Payment Service: payments table
```sql
CREATE TABLE payments (
  id VARCHAR(255) PRIMARY KEY,
  order_id VARCHAR(255) NOT NULL UNIQUE,
  user_id VARCHAR(255),
  amount DECIMAL(10, 2),
  status VARCHAR(50) NOT NULL,
  transaction_id VARCHAR(255),
  failure_reason TEXT,
  created_at TIMESTAMP DEFAULT NOW(),
  CONSTRAINT payment_status_check CHECK (
    status IN ('PENDING', 'SUCCESS', 'FAILED')
  )
);
```

## Monitoring & Observability

### Metrics
```
- order_saga_started: Counter of started sagas
- order_saga_completed: Counter of completed sagas
- order_saga_failed: Counter of failed sagas
- saga_step_duration: Histogram of each step's duration
- kafka_event_lag: Consumer lag per topic
```

### Logging Pattern
```
All logs include: [traceId] timestamp level message

Example:
2024-06-19 10:30:45 [550e8400-e29b-41d4-a716-446655440000] INFO Saga started for order: order-12345

Format (JSON):
{
  "timestamp": "2024-06-19T10:30:45Z",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "service": "orchestrator",
  "level": "INFO",
  "message": "Saga started for order: order-12345",
  "orderId": "order-12345",
  "step": "ORDER_CREATED"
}
```

## Testing Strategy

### Unit Tests
- Each listener method mocked with event payloads
- Assert correct event published to Kafka

### Integration Tests (TestContainers)
```
1. Start Kafka container
2. Publish OrderCreatedEvent
3. Assert PaymentInitiatedEvent on kafka:payment-initiated
4. Publish PaymentProcessedEvent
5. Assert ShippingInitiatedEvent on kafka:shipping-initiated
6. Verify saga completion
```

### Saga Tests
```
Happy Path:
  Order → Payment (SUCCESS) → Shipping → Delivered

Error Path:
  Order → Payment (FAILED) → Saga Failed
  
Duplicate Event:
  Send same event twice, verify idempotency
```

---

## Future Enhancements

1. **Saga Time Limit**: Fail saga if not completed in 24 hours
2. **Compensation**: Automatic refund on shipping failure
3. **Circuit Breaker**: Skip payment if too many recent failures
4. **Dead Letter Queue**: Handle poison pill events
5. **Distributed Tracing**: Use OpenTelemetry for end-to-end traces
