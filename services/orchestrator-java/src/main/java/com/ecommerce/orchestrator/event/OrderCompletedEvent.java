package com.ecommerce.orchestrator.event;

/**
 * Event published by the orchestrator when the saga finishes
 * successfully.
 *
 * <p>Consumed by the order service to mark the order as DELIVERED.
 */
public class OrderCompletedEvent {
    private String orderId;
    private String status;
    private String traceId;
    private long timestamp;

    public OrderCompletedEvent() {}

    public OrderCompletedEvent(String orderId, String status, String traceId, long timestamp) {
        this.orderId = orderId;
        this.status = status;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getTraceId() { return traceId; }
    public long getTimestamp() { return timestamp; }
}
