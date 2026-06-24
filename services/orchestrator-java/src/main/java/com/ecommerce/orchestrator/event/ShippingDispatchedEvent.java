package com.ecommerce.orchestrator.event;

/**
 * Event published by the shipping service after a shipment is dispatched.
 *
 * <p>Consumed by the orchestrator to complete the saga: publish
 * order-completed and mark the saga state as COMPLETED.
 */
public class ShippingDispatchedEvent {
    private String orderId;
    private String trackingId;
    private String traceId;
    private long timestamp;

    public ShippingDispatchedEvent() {}

    public ShippingDispatchedEvent(String orderId, String trackingId, String traceId, long timestamp) {
        this.orderId = orderId;
        this.trackingId = trackingId;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public String getOrderId() { return orderId; }
    public String getTrackingId() { return trackingId; }
    public String getTraceId() { return traceId; }
    public long getTimestamp() { return timestamp; }
}
