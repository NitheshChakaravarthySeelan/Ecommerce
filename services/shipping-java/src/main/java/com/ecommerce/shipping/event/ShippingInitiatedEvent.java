package com.ecommerce.shipping.event;

/**
 * Event consumed from Kafka to trigger shipment dispatch.
 *
 * <p>Published by the orchestrator after payment succeeds.
 * The shipping saga listener handles this event.
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
