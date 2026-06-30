package com.banking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for the application's async (background thread) execution.
 *
 * Why async?
 *  Several operations should not block the HTTP response thread:
 *   - Sending OTP / welcome / transaction alert emails (EmailService)
 *   - Writing audit log entries (AuditService)
 *   - Persisting in-app notifications (NotificationService)
 *
 * These tasks are non-critical to the core business operation.
 * If they fail, the main transaction must not roll back, so running them
 * on a separate thread also provides transactional isolation.
 *
 * Thread pool sizing:
 *  - corePoolSize (5)   : always-alive threads ready to pick up tasks immediately
 *  - maxPoolSize (15)   : maximum threads under burst load
 *  - queueCapacity (100): tasks waiting when all 15 threads are busy;
 *                         if the queue is also full, a RejectedExecutionException is thrown
 *  - awaitTermination   : on shutdown, wait up to 30 s for in-flight tasks to complete
 *                         (so emails / audit entries aren't silently dropped on deploy)
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Named "taskExecutor" so Spring's @Async uses this pool by default
     * instead of the single-threaded SimpleAsyncTaskExecutor.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);           // keep 5 threads always warm
        executor.setMaxPoolSize(15);           // scale up to 15 under heavy load
        executor.setQueueCapacity(100);        // buffer up to 100 pending tasks
        executor.setThreadNamePrefix("Banking-Async-"); // makes threads easy to identify in thread dumps
        executor.setWaitForTasksToCompleteOnShutdown(true); // drain queue on graceful shutdown
        executor.setAwaitTerminationSeconds(30);            // max wait time before forced shutdown
        executor.initialize();
        return executor;
    }

    /**
     * Tells Spring to use the above pool whenever a method is annotated @Async
     * and no explicit executor is specified.
     */
    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }
}
