package com.ecommerce.orchestrator.service;

import com.ecommerce.orchestrator.entity.SagaState;
import com.ecommerce.orchestrator.event.OrderCreatedEvent;
import com.ecommerce.orchestrator.event.PaymentProcessedEvent;
import com.ecommerce.orchestrator.event.ShippingDispatchedEvent;
import com.ecommerce.orchestrator.repository.SagaStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private SagaStateRepository sagaStateRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private Executor sagaTaskExecutor;

    private OrderSagaOrchestrator orchestrator;

    @Captor
    private ArgumentCaptor<Map<String, Object>> mapCaptor;

    @BeforeEach
    void setUp() {
        // Make the executor run tasks synchronously so tests are deterministic
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(sagaTaskExecutor).execute(any(Runnable.class));

        orchestrator = new OrderSagaOrchestrator(
            kafkaTemplate, sagaStateRepository, restTemplate, sagaTaskExecutor,
            "http://inventory-rust:8085/inventory", "http://order-python:8087/orders");
    }

    @Test
    void shouldSkipAlreadyCompletedOrder() {
        when(sagaStateRepository.existsByOrderIdAndCompletedTrue("order-1")).thenReturn(true);

        OrderCreatedEvent event = new OrderCreatedEvent("order-1", "user-1", 100.0, "trace-1", 0);
        orchestrator.handleOrderCreated(event).join();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void shouldProcessPaymentOnSuccess() {
        when(sagaStateRepository.existsByOrderIdAndCompletedTrue("order-2")).thenReturn(false);
        when(sagaStateRepository.findByOrderId("order-2")).thenReturn(Optional.empty());

        Map<String, Object> mockOrder = Map.of("items", java.util.List.of(
            Map.of("product_id", "p1", "quantity", 2)
        ));
        when(restTemplate.getForEntity(contains("/orders/order-2"), eq(Map.class)))
            .thenReturn(org.springframework.http.ResponseEntity.ok(mockOrder));
        when(restTemplate.postForEntity(contains("/inventory/batch-reserve"), any(), eq(Map.class)))
            .thenReturn(org.springframework.http.ResponseEntity.ok(Map.of("success", true)));

        OrderCreatedEvent event = new OrderCreatedEvent("order-2", "user-1", 100.0, "trace-2", 0);
        orchestrator.handleOrderCreated(event).join();

        verify(kafkaTemplate).send(eq("payment-initiated"), eq("order-2"), any());
    }

    @Test
    void shouldHandlePaymentFailure() {
        when(sagaStateRepository.existsByOrderIdAndCompletedTrue("order-3")).thenReturn(false);

        PaymentProcessedEvent event = new PaymentProcessedEvent("pay-1", "order-3", "FAILED", "trace-3", 0);
        orchestrator.handlePaymentProcessed(event).join();

        verify(kafkaTemplate).send(eq("saga-failed"), eq("order-3"), any());
    }

    @Test
    void shouldCompleteSagaOnShippingDispatched() {
        when(sagaStateRepository.existsByOrderIdAndCompletedTrue("order-4")).thenReturn(false);
        when(sagaStateRepository.findByOrderId("order-4")).thenReturn(Optional.of(new SagaState("order-4", "IN_PROGRESS", "{}", "shipping-initiated")));

        ShippingDispatchedEvent event = new ShippingDispatchedEvent("order-4", "track-1", "trace-4", 0);
        orchestrator.handleShippingDispatched(event).join();

        verify(kafkaTemplate).send(eq("order-completed"), eq("order-4"), any());
    }

    // ── Duplicate-event idempotency tests ──────────────────────────

    @Test
    void shouldSkipDuplicateOrderCreatedEvent_whenSagaAlreadyCompleted() {
        when(sagaStateRepository.existsByOrderIdAndCompletedTrue("dup-order")).thenReturn(true);

        // Send the same event twice
        OrderCreatedEvent event = new OrderCreatedEvent("dup-order", "user-1", 100.0, "trace-dup", 0);
        orchestrator.handleOrderCreated(event).join();
        orchestrator.handleOrderCreated(event).join();

        // Should not have published payment-initiated
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void shouldHandleDataIntegrityViolationOnDuplicateSagaInsert() {
        // First call: no saga state exists → tries to create one
        // Second call: no saga state exists yet (first save not committed) → also tries to create one
        // → unique constraint violation on second save
        when(sagaStateRepository.existsByOrderIdAndCompletedTrue("dup-saga")).thenReturn(false);
        when(sagaStateRepository.findByOrderId("dup-saga"))
            .thenReturn(Optional.empty())    // first call: not found
            .thenReturn(Optional.empty());   // second call: not found yet (race)

        // First save succeeds, second save throws DataIntegrityViolationException
        when(sagaStateRepository.save(any(SagaState.class)))
            .thenReturn(new SagaState("dup-saga", "IN_PROGRESS", "", "inventory-reserve"))
            .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        Map<String, Object> mockOrder = Map.of("items", java.util.List.of(
            Map.of("product_id", "p1", "quantity", 2)
        ));
        when(restTemplate.getForEntity(contains("/orders/dup-saga"), eq(Map.class)))
            .thenReturn(org.springframework.http.ResponseEntity.ok(mockOrder));
        when(restTemplate.postForEntity(contains("/inventory/batch-reserve"), any(), eq(Map.class)))
            .thenReturn(org.springframework.http.ResponseEntity.ok(Map.of("success", true)));

        // First event — should process normally
        OrderCreatedEvent event = new OrderCreatedEvent("dup-saga", "user-1", 100.0, "trace-dup", 0);
        orchestrator.handleOrderCreated(event).join();

        reset(restTemplate);

        // Second event (re-delivery) — should NOT process (DataIntegrityViolation caught)
        OrderCreatedEvent duplicate = new OrderCreatedEvent("dup-saga", "user-1", 100.0, "trace-dup", 0);
        orchestrator.handleOrderCreated(duplicate).join();

        // payment-initiated should only be sent once
        verify(kafkaTemplate, times(1)).send(eq("payment-initiated"), eq("dup-saga"), any());
    }

    @Test
    void shouldSkipDuplicatePaymentProcessed_whenAlreadyCompleted() {
        when(sagaStateRepository.existsByOrderIdAndCompletedTrue("order-pay-dup")).thenReturn(true);

        PaymentProcessedEvent event = new PaymentProcessedEvent("pay-1", "order-pay-dup", "SUCCEEDED", "trace-pay", 0);
        orchestrator.handlePaymentProcessed(event).join();
        orchestrator.handlePaymentProcessed(event).join();

        // Should NOT publish shipping-initiated (completed check filters it)
        verify(kafkaTemplate, never()).send(eq("shipping-initiated"), anyString(), any());
    }

    @Test
    void shouldSkipDuplicateShippingDispatched_whenAlreadyCompleted() {
        when(sagaStateRepository.existsByOrderIdAndCompletedTrue("order-ship-dup")).thenReturn(true);

        ShippingDispatchedEvent event = new ShippingDispatchedEvent("order-ship-dup", "track-1", "trace-ship", 0);
        orchestrator.handleShippingDispatched(event).join();
        orchestrator.handleShippingDispatched(event).join();

        // Should NOT publish order-completed (completed check filters it)
        verify(kafkaTemplate, never()).send(eq("order-completed"), anyString(), any());
    }
}
