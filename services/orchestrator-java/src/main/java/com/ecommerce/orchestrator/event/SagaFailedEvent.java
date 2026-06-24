package com.ecommerce.orchestrator.event;

/**
 * Event published by the orchestrator when a saga fails at any step.
 *
 * <p>Consumed by the order service to mark the order as FAILED.
 * The compensating transaction (inventory release) runs before this
 * event is published.
 */
public class SagaFailedEvent {
    private String orderId;
    private String reason;
    private String traceId;
    private long timestamp;

    public SagaFailedEvent() {}

    public SagaFailedEvent(String orderId, String reason, String traceId, long timestamp) {
        this.orderId = orderId;
        this.reason = reason;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public String getOrderId() { return orderId; }
    public String getReason() { return reason; }
    public String getTraceId() { return traceId; }
    public long getTimestamp() { return timestamp; }
}
