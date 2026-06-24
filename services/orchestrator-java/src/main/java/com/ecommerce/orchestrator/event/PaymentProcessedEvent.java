package com.ecommerce.orchestrator.event;

/**
 * Event published by the payment service after processing a payment.
 *
 * <p>Status is "SUCCEEDED" (amount > 0) or "FAILED" (amount ≤ 0).
 * Consumed by the orchestrator to either initiate shipping (on success)
 * or fail the saga (on failure).
 */
public class PaymentProcessedEvent {
    private String paymentId;
    private String orderId;
    private String status;
    private String traceId;
    private long timestamp;

    public PaymentProcessedEvent() {}

    public PaymentProcessedEvent(String paymentId, String orderId, String status, String traceId, long timestamp) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = status;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public String getPaymentId() { return paymentId; }
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getTraceId() { return traceId; }
    public long getTimestamp() { return timestamp; }
}
