package com.ecommerce.saga.entity;

import jakarta.persistence.*;

/**
 * Persisted state for a manually-started saga (via the saga service).
 *
 * <p>This is separate from the orchestrator's SagaState entity.
 * The saga service provides a REST entry point for starting and
 * querying sagas, while the orchestrator handles the actual
 * event-driven fulfillment flow.
 */
@Entity
@Table(name = "saga_states")
public class SagaStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sagaId;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String payload;

    public SagaStateEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
