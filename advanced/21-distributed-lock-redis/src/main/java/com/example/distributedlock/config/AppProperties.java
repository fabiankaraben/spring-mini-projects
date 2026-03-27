package com.example.distributedlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration properties for the application.
 *
 * <p>Bound from the {@code app.*} namespace in {@code application.yml}.
 * Spring Boot validates these values at startup so misconfigured deployments
 * fail fast rather than silently using wrong values.
 *
 * <p>Usage example in {@code application.yml}:
 * <pre>
 * app:
 *   lock:
 *     timeout-seconds: 5
 *     lease-seconds: 30
 *   task:
 *     processing-ms: 2000
 * </pre>
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Distributed lock configuration knobs. */
    private final Lock lock = new Lock();

    /** Task processing simulation configuration. */
    private final Task task = new Task();

    public Lock getLock() {
        return lock;
    }

    public Task getTask() {
        return task;
    }

    // -------------------------------------------------------------------------
    // Nested configuration class: Lock
    // -------------------------------------------------------------------------

    /**
     * Configuration for the distributed lock behaviour.
     */
    public static class Lock {

        /**
         * Maximum time (seconds) a caller will wait to acquire the lock.
         *
         * <p>If the lock is not available within this window the caller receives
         * a 409 Conflict response rather than waiting indefinitely. This prevents
         * request pile-up under heavy load.
         */
        private long timeoutSeconds = 5;

        /**
         * Maximum time (seconds) the lock will be held.
         *
         * <p>This is the lease time (TTL) of the Redis key that backs the lock.
         * If the lock holder crashes before releasing the lock, Redis will
         * automatically expire the key after this many seconds, preventing
         * permanent dead-locks in the system.
         *
         * <p>Set to {@code -1} to delegate TTL management to Redisson's watch-dog
         * mechanism (the watch-dog renews the lease every 10 seconds while the
         * holder is alive). Explicit leaseTime is recommended for production.
         */
        private long leaseSeconds = 30;

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public long getLeaseSeconds() {
            return leaseSeconds;
        }

        public void setLeaseSeconds(long leaseSeconds) {
            this.leaseSeconds = leaseSeconds;
        }
    }

    // -------------------------------------------------------------------------
    // Nested configuration class: Task
    // -------------------------------------------------------------------------

    /**
     * Configuration for the simulated task processing.
     */
    public static class Task {

        /**
         * Simulated processing time in milliseconds.
         *
         * <p>The TaskService sleeps for this duration while holding the lock,
         * making lock contention visible and easy to demonstrate with concurrent
         * HTTP requests.
         */
        private long processingMs = 2000;

        public long getProcessingMs() {
            return processingMs;
        }

        public void setProcessingMs(long processingMs) {
            this.processingMs = processingMs;
        }
    }
}
