package com.example.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration class to enable and configure asynchronous method execution.
 * 
 * @EnableAsync tells Spring to start looking for @Async annotations on methods
 *              and run them in background thread pools.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Custom ThreadPoolTaskExecutor to control how async tasks are executed.
     * By defining this bean, Spring uses it instead of the default
     * SimpleAsyncTaskExecutor.
     *
     * @return an Executor instance
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Set the core number of threads to keep alive
        executor.setCorePoolSize(2);
        // Set the maximum allowed number of threads
        executor.setMaxPoolSize(5);
        // Set the capacity for the queue that holds tasks waiting to be executed
        executor.setQueueCapacity(10);
        // Set a prefix for the names of newly created threads (useful for debugging)
        executor.setThreadNamePrefix("AsyncThread-");
        // Initialize the executor
        executor.initialize();
        return executor;
    }
}
