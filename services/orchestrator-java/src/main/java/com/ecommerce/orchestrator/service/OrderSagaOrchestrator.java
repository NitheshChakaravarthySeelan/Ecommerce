package com.ecommerce.orchestrator.service;

import com.ecommerce.orchestrator.entity.SagaState;
import com.ecommerce.orchestrator.event.*;
import com.ecommerce.orchestrator.repository.SagaStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Orchestrator for the order fulfillment saga.
 *
 * <p>This is the central coordinator of the distributed transaction.
 * It listens to three Kafka topics and advances the saga through its
 * lifecycle:
 *
 * <ol>
 *   <li><b>order-created</b> → fetch items, reserve inventory, publish payment-initiated</li>
 *   <li><b>payment-processed</b> → if succeeded → publish shipping-initiated;
 *       if failed → compensate (release inventory, publish saga-failed)</li>
 *   <li><b>shipping-dispatched</b> → publish order-completed, mark saga COMPLETED</li>
 * </ol>
 *
 * <h3>Async execution model</h3>
 * The {@code @KafkaListener} methods run on consumer threads. To avoid
 * blocking them with HTTP I/O, each listener performs a quick
 * idempotency check ({@link #isAlreadyCompleted}) and saga lock
 * ({@link #acquireSaga}) on the consumer thread, then offloads the
 * actual processing to the {@code sagaTaskExecutor} thread pool via
 * {@link CompletableFuture#supplyAsync}. The returned future tells
 * Spring Kafka to hold the offset commit until processing finishes.
 *
 * <h3>Compensating transactions (failSaga)</h3>
 * If any step fails, {@link #failSaga} is called:
 * <ol>
 *   <li>Release all reserved inventory (batch REST call)</li>
 *   <li>Publish {@code saga-failed} event</li>
 *   <li>Mark saga state as FAILED and completed</li>
 * </ol>
 *
 * <h3>Idempotency</h3>
 * Duplicate events are detected via {@link SagaState#isCompleted()}
 * and the database unique constraint on {@code orderId}. Optimistic
 * locking ({@code @Version}) prevents concurrent processing of the
 * same order.
 *
 * <h3>Configuration</h3>
 * Service URLs are hardcoded constants — in production these should
 * come from service discovery or environment variables.
 *
 * @see AsyncConfig (sagaTaskExecutor thread pool)
 * @see SagaState (persistent saga state entity)
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SagaStateRepository sagaStateRepository;
    private final RestTemplate restTemplate;
    private final Executor sagaTaskExecutor;

    public OrderSagaOrchestrator(KafkaTemplate<String, Object> kafkaTemplate,
                                  SagaStateRepository sagaStateRepository,
                                  RestTemplate restTemplate,
                                  @Qualifier("sagaTaskExecutor") Executor sagaTaskExecutor,
                                  @Value("${inventory.url:http://inventory-rust:8085/inventory}") String inventoryUrl,
                                  @Value("${order.url:http://order-python:8087/orders}") String orderUrl) {
        this.kafkaTemplate = kafkaTemplate;
        this.sagaStateRepository = sagaStateRepository;
        this.restTemplate = restTemplate;
        this.sagaTaskExecutor = sagaTaskExecutor;
        this.INVENTORY_URL = inventoryUrl;
        this.ORDER_URL = orderUrl;
    }

    private final String INVENTORY_URL;
    private final String ORDER_URL;

    // ──────────────────────────────────────────────
    // Internal HTTP helpers
    // ──────────────────────────────────────────────

    /** Fetch full order data (including item list) from the order service. */
    private Map<String, Object> fetchOrderItems(String orderId) {
        String url = ORDER_URL + "/" + orderId;
        ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Failed to fetch order details for " + orderId);
        }
        return resp.getBody();
    }

    /**
     * Reserve inventory for all items in one batch HTTP call.
     * The inventory service runs this in a single DB transaction
     * and rolls back everything if any item has insufficient stock.
     */
    private void batchReserveInventory(String orderId, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return;
        List<Map<String, Object>> inventoryItems = items.stream()
            .map(item -> Map.of("product_id", item.get("product_id"), "quantity", item.get("quantity")))
            .toList();
        Map<String, Object> req = Map.of("order_id", orderId, "items", inventoryItems);
        ResponseEntity<Map> resp = restTemplate.postForEntity(INVENTORY_URL + "/batch-reserve", req, Map.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Batch inventory reservation failed for order: " + orderId);
        }
        Map<String, Object> result = resp.getBody();
        if (result != null && Boolean.FALSE.equals(result.get("success"))) {
            throw new RuntimeException("Insufficient stock: " + result.get("message"));
        }
    }

    /**
     * Release inventory for all items in one batch HTTP call.
     * This is the compensating action when a saga fails.
     */
    private void batchReleaseInventory(String orderId, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return;
        List<Map<String, Object>> inventoryItems = items.stream()
            .map(item -> Map.of("product_id", item.get("product_id"), "quantity", item.get("quantity")))
            .toList();
        Map<String, Object> req = Map.of("order_id", orderId, "items", inventoryItems);
        restTemplate.postForEntity(INVENTORY_URL + "/batch-release", req, Map.class);
    }

    /** Fetch order items and release all inventory (used by failSaga). */
    private void releaseInventory(String orderId) {
        try {
            Map<String, Object> orderData = fetchOrderItems(orderId);
            List<Map<String, Object>> items = (List<Map<String, Object>>) orderData.get("items");
            batchReleaseInventory(orderId, items);
            log.info("Inventory released for failed order: {}", orderId);
        } catch (Exception e) {
            log.warn("Failed to release inventory for order: {}", orderId, e);
        }
    }

    // ──────────────────────────────────────────────
    // Kafka Listeners — run on consumer threads
    // ──────────────────────────────────────────────

    /**
     * Handle {@code order-created} event.
     *
     * <p>Quick checks on consumer thread:
     * <ol>
     *   <li>Skip if already completed</li>
     *   <li>Acquire saga lock (optimistic locking with retry)</li>
     * </ol>
     *
     * <p>Heavy work offloaded to {@code sagaTaskExecutor}:
     * <ol>
     *   <li>Fetch order details from order service</li>
     *   <li>Reserve inventory (batch call to inventory service)</li>
     *   <li>Publish {@code payment-initiated} event</li>
     * </ol>
     */
    @KafkaListener(topics = "order-created", groupId = "orchestrator-group")
    public CompletableFuture<Void> handleOrderCreated(OrderCreatedEvent event) {
        if (isAlreadyCompleted(event.getOrderId())) {
            return CompletableFuture.completedFuture(null);
        }
        if (!acquireSaga(event.getOrderId(), "inventory-reserve")) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            MDC.put("traceId", event.getTraceId());
            try {
                processOrderCreated(event);
            } catch (Exception e) {
                log.error("Failed to process order: {}", event.getOrderId(), e);
                failSaga(event.getOrderId(), event.getTraceId(), e.getMessage());
            } finally {
                MDC.clear();
            }
            return null;
        }, sagaTaskExecutor);
    }

    /**
     * Handle {@code payment-processed} event.
     *
     * <p>On consumer thread: check completed status.
     * If FAILED → submit to executor for compensating actions.
     * If SUCCEEDED → acquire lock, then submit shipping initiation.
     */
    @KafkaListener(topics = "payment-processed", groupId = "orchestrator-group")
    public CompletableFuture<Void> handlePaymentProcessed(PaymentProcessedEvent event) {
        if (isAlreadyCompleted(event.getOrderId())) {
            return CompletableFuture.completedFuture(null);
        }
        if ("FAILED".equals(event.getStatus())) {
            return CompletableFuture.supplyAsync(() -> {
                MDC.put("traceId", event.getTraceId());
                try {
                    log.error("Payment failed for order: {}", event.getOrderId());
                    failSaga(event.getOrderId(), event.getTraceId(), "Payment failed");
                } finally {
                    MDC.clear();
                }
                return null;
            }, sagaTaskExecutor);
        }
        if (!acquireSaga(event.getOrderId(), "shipping-initiated")) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            MDC.put("traceId", event.getTraceId());
            try {
                processPaymentSucceeded(event);
            } catch (Exception e) {
                log.error("Failed to process payment for order: {}", event.getOrderId(), e);
                failSaga(event.getOrderId(), event.getTraceId(), e.getMessage());
            } finally {
                MDC.clear();
            }
            return null;
        }, sagaTaskExecutor);
    }

    /**
     * Handle {@code shipping-dispatched} event.
     *
     * <p>On consumer thread: check completed status, then submit
     * saga completion logic to the executor.
     */
    @KafkaListener(topics = "shipping-dispatched", groupId = "orchestrator-group")
    public CompletableFuture<Void> handleShippingDispatched(ShippingDispatchedEvent event) {
        if (isAlreadyCompleted(event.getOrderId())) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            MDC.put("traceId", event.getTraceId());
            try {
                processShippingDispatched(event);
            } catch (Exception e) {
                log.error("Failed to complete order: {}", event.getOrderId(), e);
                failSaga(event.getOrderId(), event.getTraceId(), e.getMessage());
            } finally {
                MDC.clear();
            }
            return null;
        }, sagaTaskExecutor);
    }

    // ──────────────────────────────────────────────
    // Processing methods — run on saga worker threads
    // Package-private for testability
    // ──────────────────────────────────────────────

    /** Fetch order details, reserve all inventory, and trigger payment. */
    void processOrderCreated(OrderCreatedEvent event) {
        log.info("Saga started for order: {}", event.getOrderId());
        Map<String, Object> orderData = fetchOrderItems(event.getOrderId());
        List<Map<String, Object>> items = (List<Map<String, Object>>) orderData.get("items");
        batchReserveInventory(event.getOrderId(), items);
        log.info("Inventory reserved for order: {}", event.getOrderId());

        PaymentInitiatedEvent paymentEvent = new PaymentInitiatedEvent(
            event.getOrderId(), event.getUserId(), event.getTotalAmount(),
            event.getTraceId(), System.currentTimeMillis()
        );
        kafkaTemplate.send("payment-initiated", event.getOrderId(), paymentEvent);
        log.info("Payment initiated for order: {}", event.getOrderId());
    }

    /** Payment succeeded — trigger shipping. */
    void processPaymentSucceeded(PaymentProcessedEvent event) {
        log.info("Payment succeeded for order: {}", event.getOrderId());
        ShippingInitiatedEvent shippingEvent = new ShippingInitiatedEvent(
            event.getOrderId(), event.getTraceId(), System.currentTimeMillis()
        );
        kafkaTemplate.send("shipping-initiated", event.getOrderId(), shippingEvent);
        log.info("Shipping initiated for order: {}", event.getOrderId());
    }

    /** Shipping dispatched — mark saga as COMPLETED. */
    void processShippingDispatched(ShippingDispatchedEvent event) {
        log.info("Shipping dispatched for order: {}", event.getOrderId());
        OrderCompletedEvent completedEvent = new OrderCompletedEvent(
            event.getOrderId(), "DELIVERED", event.getTraceId(), System.currentTimeMillis()
        );
        kafkaTemplate.send("order-completed", event.getOrderId(), completedEvent);
        log.info("Saga completed for order: {}", event.getOrderId());

        sagaStateRepository.findByOrderId(event.getOrderId()).ifPresent(state -> {
            state.setStatus("COMPLETED");
            state.setCurrentStep("completed");
            state.setCompleted(true);
            sagaStateRepository.save(state);
        });
    }

    /** Check if this order's saga is already marked completed. */
    private boolean isAlreadyCompleted(String orderId) {
        if (sagaStateRepository.existsByOrderIdAndCompletedTrue(orderId)) {
            log.info("Order {} already completed, skipping", orderId);
            return true;
        }
        return false;
    }

    /**
     * Acquire an exclusive saga lock for this order + step.
     *
     * <p>Uses optimistic locking ({@code @Version}) with up to 3 retries
     * and 100 ms back-off. If a duplicate saga row is detected (unique
     * constraint on {@code orderId}), this is treated as a concurrent
     * duplicate event and the method returns false.
     *
     * <p>The unique constraint on {@code orderId} is the final safety
     * net against race conditions in the check-then-insert pattern.
     */
    private boolean acquireSaga(String orderId, String step) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Optional<SagaState> existing = sagaStateRepository.findByOrderId(orderId);
                if (existing.isPresent()) {
                    SagaState state = existing.get();
                    if (state.isCompleted()) return false;
                    if (state.getRetryCount() >= 3) {
                        log.warn("Order {} exceeded max retries, failing saga", orderId);
                        return false;
                    }
                    state.setRetryCount(state.getRetryCount() + 1);
                    state.setCurrentStep(step);
                    sagaStateRepository.save(state);
                } else {
                    SagaState state = new SagaState(orderId, "IN_PROGRESS", "", step);
                    sagaStateRepository.save(state);
                }
                return true;
            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict for order {}, attempt {}/3", orderId, attempt + 1);
                if (attempt == 2) {
                    log.error("Failed to acquire saga lock for order {} after 3 attempts", orderId);
                    return false;
                }
                try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
            } catch (DataIntegrityViolationException e) {
                // Unique constraint on saga_states.orderId — a duplicate
                // event was processed concurrently. Idempotent skip.
                log.warn("Duplicate saga state for order {} (unique constraint), skipping", orderId);
                return false;
            }
        }
        return false;
    }

    /**
     * Compensating transaction for a failed saga.
     *
     * <ol>
     *   <li>Release all reserved inventory (best-effort, logs warning on failure)</li>
     *   <li>Publish {@code saga-failed} event</li>
     *   <li>Mark saga state as FAILED and completed</li>
     * </ol>
     */
    private void failSaga(String orderId, String traceId, String reason) {
        log.error("Saga failing for order: {} - {}", orderId, reason);
        releaseInventory(orderId);

        SagaFailedEvent failedEvent = new SagaFailedEvent(orderId, reason, traceId, System.currentTimeMillis());
        kafkaTemplate.send("saga-failed", orderId, failedEvent);
        log.error("Saga failed event published for order: {}", orderId);

        sagaStateRepository.findByOrderId(orderId).ifPresent(state -> {
            state.setStatus("FAILED");
            state.setCurrentStep("failed");
            state.setCompleted(true);
            sagaStateRepository.save(state);
        });
    }
}
