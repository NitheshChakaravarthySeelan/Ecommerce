package com.ecommerce.orchestrator.repository;

import com.ecommerce.orchestrator.entity.SagaState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class SagaStateRepositoryTest {

    @Autowired
    private SagaStateRepository repository;

    @Test
    void shouldSaveAndFindByOrderId() {
        SagaState state = new SagaState("order-123", "IN_PROGRESS", "{}", "inventory-reserve");
        repository.save(state);

        SagaState found = repository.findByOrderId("order-123").orElse(null);
        assertNotNull(found);
        assertEquals("order-123", found.getOrderId());
        assertEquals("IN_PROGRESS", found.getStatus());
    }

    @Test
    void shouldDetectCompletedOrder() {
        SagaState state = new SagaState("order-456", "COMPLETED", "{}", "completed");
        state.setCompleted(true);
        repository.save(state);

        assertTrue(repository.existsByOrderIdAndCompletedTrue("order-456"));
        assertFalse(repository.existsByOrderIdAndCompletedTrue("order-999"));
    }

    @Test
    void shouldUpdateSagaState() {
        SagaState state = new SagaState("order-789", "IN_PROGRESS", "{}", "inventory-reserve");
        repository.save(state);

        SagaState loaded = repository.findByOrderId("order-789").orElseThrow();
        loaded.setCurrentStep("payment-initiated");
        loaded.setStatus("PAYMENT_PENDING");
        repository.save(loaded);

        SagaState updated = repository.findByOrderId("order-789").orElseThrow();
        assertEquals("payment-initiated", updated.getCurrentStep());
        assertEquals("PAYMENT_PENDING", updated.getStatus());
    }
}
