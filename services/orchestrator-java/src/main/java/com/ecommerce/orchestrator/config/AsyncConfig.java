package com.ecommerce.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pool configuration for async saga processing.
 *
 * <p>This executor is used by {@code OrderSagaOrchestrator} to offload
 * HTTP-intensive saga steps (inventory reservation, order fetching)
 * from the Kafka consumer threads to a dedicated pool of worker threads.
 *
 * <p>Configuration rationale:
 * <ul>
 *   <li><b>corePoolSize=4</b> — enough for concurrent saga processing
 *       without overwhelming downstream services</li>
 *   <li><b>maxPoolSize=8</b> — burst capacity for traffic spikes</li>
 *   <li><b>queueCapacity=100</b> — absorbs bursts beyond 8 threads</li>
 *   <li><b>waitForTasksToCompleteOnShutdown=true</b> — prevents
 *       in-flight sagas from being interrupted during rollout</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "sagaTaskExecutor")
    public Executor sagaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("saga-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
