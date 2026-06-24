package com.ecommerce.orchestrator.service;

import com.ecommerce.orchestrator.entity.SagaState;
import com.ecommerce.orchestrator.event.SagaFailedEvent;
import com.ecommerce.orchestrator.repository.SagaStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled task that detects and fails sagas stuck IN_PROGRESS.
 *
 * <p>If a Kafka event is lost (e.g., payment-processed never arrives),
 * the saga remains IN_PROGRESS indefinitely. This task periodically
 * sweeps for sagas that have not been updated within the configured
 * timeout and marks them as FAILED.
 */
@Component
public class SagaTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutTask.class);

    private final SagaStateRepository sagaStateRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final int sagaTimeoutMinutes;

    public SagaTimeoutTask(SagaStateRepository sagaStateRepository,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           @Value("${saga.timeout-minutes:30}") int sagaTimeoutMinutes) {
        this.sagaStateRepository = sagaStateRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.sagaTimeoutMinutes = sagaTimeoutMinutes;
    }

    /**
     * Run every 60 seconds. Finds sagas that have been IN_PROGRESS
     * without any update for longer than the configured timeout.
     * Marks them as FAILED and publishes saga-failed event.
     */
    @Scheduled(fixedRate = 60_000)
    public void timeoutStuckSagas() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(sagaTimeoutMinutes);
        List<SagaState> stuck = sagaStateRepository.findStuckSagas(cutoff);

        for (SagaState state : stuck) {
            try {
                log.warn("Timing out stuck saga for order: {} (last updated: {})", state.getOrderId(), state.getLastUpdatedAt());
                state.setStatus("FAILED");
                state.setCurrentStep("timed-out");
                state.setCompleted(true);
                sagaStateRepository.save(state);

                SagaFailedEvent event = new SagaFailedEvent(
                    state.getOrderId(), "Saga timed out after " + sagaTimeoutMinutes + " minutes",
                    "saga-timeout", System.currentTimeMillis()
                );
                kafkaTemplate.send("saga-failed", state.getOrderId(), event);
                log.warn("Stuck saga for order {} failed by timeout", state.getOrderId());
            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict timing out saga for order {}, skipping", state.getOrderId());
            }
        }

        if (!stuck.isEmpty()) {
            log.info("Timed out {} stuck saga(s)", stuck.size());
        }
    }
}
