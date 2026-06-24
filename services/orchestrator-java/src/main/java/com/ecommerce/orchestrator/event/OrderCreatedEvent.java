package com.ecommerce.orchestrator.event;

/**
 * Event published by the order service when a customer places an order.
 *
 * <p>Consumed by the orchestrator to start the fulfillment saga:
 * reserve inventory → initiate payment.
 */
public class OrderCreatedEvent {
    private String orderId;
    private String userId;
    private double totalAmount;
    private String traceId;
    private long timestamp;

    public OrderCreatedEvent() {}

    public OrderCreatedEvent(String orderId, String userId, double totalAmount, String traceId, long timestamp) {
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public double getTotalAmount() { return totalAmount; }
    public String getTraceId() { return traceId; }
    public long getTimestamp() { return timestamp; }
}
