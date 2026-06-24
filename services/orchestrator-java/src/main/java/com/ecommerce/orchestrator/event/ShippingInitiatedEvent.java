package com.ecommerce.orchestrator.event;

/**
 * Event published by the orchestrator when payment succeeds.
 *
 * <p>Consumed by the shipping service to dispatch the shipment.
 */
public class ShippingInitiatedEvent {
    private String orderId;
    private String traceId;
    private long timestamp;

    public ShippingInitiatedEvent() {}

    public ShippingInitiatedEvent(String orderId, String traceId, long timestamp) {
        this.orderId = orderId;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public String getOrderId() { return orderId; }
    public String getTraceId() { return traceId; }
    public long getTimestamp() { return timestamp; }
}
