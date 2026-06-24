package com.ecommerce.orchestrator.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Persisted state for each order's saga execution.
 *
 * <p>Each row tracks one order through the fulfillment saga. The
 * {@code @Version} field enables optimistic locking so that duplicate
 * Kafka events or concurrent listeners do not double-process an order.
 *
 * <p>State machine:
 * <pre>
 *   (new) ──→ acquireSaga("inventory-reserve") ──→ IN_PROGRESS
 *       │                                              │
 *       ├── on success ──→ acquireSaga("shipping-initiated") ──→ ... ──→ COMPLETED
 *       └── on failure ──→ failSaga() ──→ FAILED
 * </pre>
 */
@Entity
@Table(name = "saga_states")
public class SagaState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private String currentStep;

    @Version
    private Long version;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime lastUpdatedAt = LocalDateTime.now();

    public SagaState() {}

    public SagaState(String orderId, String status, String payload, String currentStep) {
        this.orderId = orderId;
        this.status = status;
        this.payload = payload;
        this.currentStep = currentStep;
        this.createdAt = LocalDateTime.now();
        this.lastUpdatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}
