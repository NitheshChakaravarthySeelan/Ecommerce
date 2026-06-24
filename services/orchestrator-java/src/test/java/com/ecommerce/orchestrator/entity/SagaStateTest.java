package com.ecommerce.orchestrator.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SagaStateTest {

    @Test
    void shouldCreateSagaState() {
        SagaState state = new SagaState("order-123", "IN_PROGRESS", "{}", "payment-initiated");
        assertEquals("order-123", state.getOrderId());
        assertEquals("IN_PROGRESS", state.getStatus());
        assertEquals("payment-initiated", state.getCurrentStep());
        assertFalse(state.isCompleted());
        assertEquals(0, state.getRetryCount());
    }

    @Test
    void shouldMarkCompleted() {
        SagaState state = new SagaState("order-123", "IN_PROGRESS", "{}", "payment-initiated");
        state.setCompleted(true);
        state.setStatus("COMPLETED");
        assertTrue(state.isCompleted());
        assertEquals("COMPLETED", state.getStatus());
    }

    @Test
    void shouldIncrementRetryCount() {
        SagaState state = new SagaState("order-123", "IN_PROGRESS", "{}", "payment-initiated");
        state.setRetryCount(1);
        assertEquals(1, state.getRetryCount());
        state.setRetryCount(3);
        assertEquals(3, state.getRetryCount());
    }
}
