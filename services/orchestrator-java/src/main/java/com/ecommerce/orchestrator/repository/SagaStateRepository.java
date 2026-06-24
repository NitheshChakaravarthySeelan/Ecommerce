package com.ecommerce.orchestrator.repository;

import com.ecommerce.orchestrator.entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
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

    /** Find IN_PROGRESS sagas that have not been updated since the given timestamp. */
    @Query("SELECT s FROM SagaState s WHERE s.completed = false AND s.status = 'IN_PROGRESS' AND s.lastUpdatedAt < :cutoff")
    List<SagaState> findStuckSagas(LocalDateTime cutoff);
}
