package com.ecommerce.shipping.service;

import com.ecommerce.shipping.entity.ShippingEntity;
import com.ecommerce.shipping.event.ShippingDispatchedEvent;
import com.ecommerce.shipping.event.ShippingInitiatedEvent;
import com.ecommerce.shipping.repository.ShippingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Kafka event listener for the shipping saga step.
 *
 * <p>Consumes {@code shipping-initiated} events published by the
 * orchestrator after payment succeeds. Dispatches the shipment
 * (marks it as DISPATCHED with an estimated delivery date) and
 * publishes a {@code shipping-dispatched} event so the orchestrator
 * can complete the saga.
 *
 * <p>Saga data flow:
 * <pre>
 *   orchestrator ──→ shipping-initiated ──→ ShippingSagaListener
 *                                                  │
 *                                          save to DB + publish
 *                                                  │
 *                                                  ▼
 *                                  shipping-dispatched ──→ orchestrator
 * </pre>
 */
@Service
public class ShippingSagaListener {

    private static final Logger log = LoggerFactory.getLogger(ShippingSagaListener.class);

    private final ShippingRepository shippingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ShippingSagaListener(ShippingRepository shippingRepository,
                                KafkaTemplate<String, Object> kafkaTemplate) {
        this.shippingRepository = shippingRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Handle a {@code shipping-initiated} event from the orchestrator.
     *
     * <p>Creates or updates the shipment record with status "DISPATCHED"
     * and an estimated delivery date 5 days from now, then publishes
     * a {@code shipping-dispatched} event back to Kafka.
     *
     * <p>Exceptions are rethrown and handled by {@code DefaultErrorHandler}
     * which retries 3 times then publishes the failed message to a DLQ
     * topic ({@code shipping-initiated.DLT}).
     */
    @KafkaListener(topics = "shipping-initiated", groupId = "shipping-group")
    public void handleShippingInitiated(ShippingInitiatedEvent event) {
        MDC.put("traceId", event.getTraceId());
        log.info("Received shipping-initiated for order: {}", event.getOrderId());

        try {
            ShippingEntity entity = shippingRepository.findByOrderId(event.getOrderId());
            if (entity == null) {
                entity = new ShippingEntity();
                entity.setOrderId(event.getOrderId());
            }
            entity.setStatus("DISPATCHED");
            entity.setEstimatedDelivery(LocalDate.now().plusDays(5).toString());
            shippingRepository.save(entity);

            ShippingDispatchedEvent dispatchedEvent = new ShippingDispatchedEvent(
                event.getOrderId(),
                UUID.randomUUID().toString(),
                event.getTraceId(),
                System.currentTimeMillis()
            );

            kafkaTemplate.send("shipping-dispatched", event.getOrderId(), dispatchedEvent);
            log.info("Published shipping-dispatched for order: {} [traceId: {}]", event.getOrderId(), event.getTraceId());
        } catch (Exception e) {
            log.error("Failed to process shipping for order: {} [traceId: {}]", event.getOrderId(), event.getTraceId(), e);
            throw e; // rethrow so the DLQ error handler catches and routes to DLQ
        } finally {
            MDC.clear();
        }
    }
}
