package com.ecommerce.orchestrator.repository;

import com.ecommerce.orchestrator.entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Data access for saga state records.
 *
 * <p>Used by the orchestrator to persist and query saga progress,
 * detect duplicate events, and apply optimistic locking.
 */
public interface SagaStateRepository extends JpaRepository<SagaState, Long> {
    Optional<SagaState> findByOrderId(String orderId);
    boolean existsByOrderIdAndCompletedTrue(String orderId);
}
