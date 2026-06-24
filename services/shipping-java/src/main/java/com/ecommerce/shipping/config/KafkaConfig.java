package com.ecommerce.shipping.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer configuration with Dead Letter Queue (DLQ) support.
 *
 * <p>When a {@code @KafkaListener} throws an exception, the error handler
 * retries up to 3 times (100 ms apart), then publishes the failed message
 * to a DLQ topic ({@code <original-topic>.DLT}). This prevents message loss.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(100L, 3));
        handler.addNotRetryableExceptions(org.springframework.dao.DataIntegrityViolationException.class);
        return handler;
    }
}
