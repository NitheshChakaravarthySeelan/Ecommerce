package com.ecommerce.saga.controller;

import com.ecommerce.saga.entity.SagaStateEntity;
import com.ecommerce.saga.repository.SagaStateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for managing saga instances manually.
 *
 * <p>This service is a secondary entry point for saga workflows.
 * The primary saga orchestration is driven by the orchestrator service
 * via Kafka. This controller exists for manual/administrative triggers
 * and status queries.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /saga/start       — start a new saga, persist state, publish event</li>
 *   <li>GET  /saga/{sagaId}    — query saga status from the database</li>
 * </ul>
 */
@CrossOrigin(origins = "*")
@RestController
public class SagaController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SagaStateRepository sagaStateRepository;

    public SagaController(KafkaTemplate<String, String> kafkaTemplate,
                          ObjectMapper objectMapper,
                          SagaStateRepository sagaStateRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.sagaStateRepository = sagaStateRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "saga"));
    }

    /**
     * Start a new saga manually.
     *
     * <p>Generates a unique saga ID, persists the initial {@code STARTED}
     * state to PostgreSQL, and publishes a {@code saga-events} Kafka event
     * with the provided payload.
     *
     * @param payload arbitrary JSON payload for the saga
     * @return the created saga ID and status
     */
    @PostMapping("/saga/start")
    public ResponseEntity<Map<String, Object>> startSaga(@RequestBody Map<String, Object> payload)
            throws JsonProcessingException {
        String sagaId = UUID.randomUUID().toString();

        SagaStateEntity entity = new SagaStateEntity();
        entity.setSagaId(sagaId);
        entity.setStatus("STARTED");
        entity.setPayload(objectMapper.writeValueAsString(payload));
        sagaStateRepository.save(entity);

        Map<String, Object> event = Map.of(
            "sagaId", sagaId,
            "type", "SagaStarted",
            "payload", payload
        );
        kafkaTemplate.send("saga-events", objectMapper.writeValueAsString(event));

        return ResponseEntity.ok(Map.of(
            "sagaId", sagaId,
            "status", entity.getStatus(),
            "payload", payload
        ));
    }

    /**
     * Query the status of a saga by its ID.
     *
     * @param sagaId the saga UUID
     * @return 200 with saga ID and status, or 404 if not found
     */
    @GetMapping("/saga/{sagaId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable(name = "sagaId") String sagaId) {
        SagaStateEntity entity = sagaStateRepository.findBySagaId(sagaId);
        if (entity != null) {
            return ResponseEntity.ok(Map.of(
                "sagaId", entity.getSagaId(),
                "status", entity.getStatus()
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
