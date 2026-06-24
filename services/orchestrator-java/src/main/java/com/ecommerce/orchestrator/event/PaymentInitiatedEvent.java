package com.ecommerce.orchestrator.event;

/**
 * Event published by the orchestrator after inventory is reserved.
 *
 * <p>Consumed by the payment service to process the payment (mock:
 * succeeds if amount > 0).
 */
public class PaymentInitiatedEvent {
    private String orderId;
    private String userId;
    private double amount;
    private String traceId;
    private long timestamp;

    public PaymentInitiatedEvent() {}

    public PaymentInitiatedEvent(String orderId, String userId, double amount, String traceId, long timestamp) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public double getAmount() { return amount; }
    public String getTraceId() { return traceId; }
    public long getTimestamp() { return timestamp; }
}
