package com.example.distributedlock.domain;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.distributedlock.config.AppProperties;

/**
 * Core service that executes tasks while holding a Redis-backed distributed lock.
 *
 * <h2>The distributed locking problem</h2>
 * In a horizontally-scaled deployment many JVM instances run the same code
 * simultaneously.  A regular Java {@code synchronized} block only serialises
 * threads within <em>one</em> JVM.  If the same logical task (e.g. "generate
 * the monthly report") is triggered on two nodes at the same instant, both will
 * run concurrently, potentially causing duplicate work, data corruption, or
 * resource exhaustion.
 *
 * <h2>How Redisson's RLock works</h2>
 * Redisson implements a distributed reentrant lock on top of Redis using a
 * Lua script that atomically checks and sets a key.  The Redis SET NX PX
 * semantics guarantee that at most one caller succeeds:
 * <ol>
 *   <li>Caller A executes: {@code SET lock:report-generation <uuid>A NX PX 30000}.</li>
 *   <li>Redis sets the key — caller A now holds the lock.</li>
 *   <li>Caller B executes the same command: Redis rejects it (NX = only if Not eXists).</li>
 *   <li>Caller B waits (polls Redis) or returns immediately depending on {@code tryLock} args.</li>
 *   <li>When A finishes it calls {@code unlock()}, which deletes the key via Lua script
 *       (the script verifies the UUID so only the owner can release it).</li>
 *   <li>Caller B's next poll succeeds and it proceeds.</li>
 * </ol>
 *
 * <h2>Dead-lock prevention</h2>
 * The lock key has a TTL (leaseTime).  If the holder crashes before calling
 * {@code unlock()}, Redis expires the key automatically after {@code leaseSeconds},
 * so the lock is never permanently stuck.
 *
 * <h2>Timeout / fail-fast behaviour</h2>
 * {@link RLock#tryLock(long, long, TimeUnit)} returns {@code false} if the lock
 * is not available within {@code timeoutSeconds}.  The service translates this to
 * a {@link TaskResult} with status {@link TaskStatus#SKIPPED}, letting the HTTP
 * layer return a 409 Conflict instead of blocking the caller indefinitely.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    /**
     * Prefix for all lock keys in Redis.
     * Using a namespace prevents accidental collisions with other keys in the same
     * Redis instance that might happen to share the same name.
     */
    static final String LOCK_KEY_PREFIX = "task-lock:";

    private final RedissonClient redissonClient;
    private final AppProperties props;

    /**
     * Constructs the service with its required collaborators.
     *
     * @param redissonClient the Redisson client connected to Redis; auto-configured
     *                       by the Redisson Spring Boot starter from application.yml
     * @param props          application-level configuration (timeout, lease, processing time)
     */
    public TaskService(RedissonClient redissonClient, AppProperties props) {
        this.redissonClient = redissonClient;
        this.props = props;
    }

    /**
     * Attempts to execute a task identified by {@code taskKey} while holding a
     * distributed Redis lock.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Obtain a Redisson {@link RLock} for the key {@code "task-lock:<taskKey>"}.</li>
     *   <li>Call {@code tryLock(timeoutSeconds, leaseSeconds, SECONDS)}:
     *       <ul>
     *         <li>Returns {@code true}  → this node acquired the lock → execute task.</li>
     *         <li>Returns {@code false} → lock held by another node → return SKIPPED.</li>
     *       </ul>
     *   </li>
     *   <li>If acquired, simulate task processing with a configurable sleep.</li>
     *   <li>Release the lock in a {@code finally} block to guarantee release even
     *       if an exception is thrown during processing.</li>
     * </ol>
     *
     * @param taskKey the logical identifier for the task; used as the Redis lock key suffix
     * @param payload arbitrary data to be "processed" by the task (logged for demonstration)
     * @return a {@link TaskResult} describing what happened
     */
    public TaskResult executeWithLock(String taskKey, String payload) {
        // Build the full Redis key, e.g. "task-lock:report-generation"
        String lockKey = LOCK_KEY_PREFIX + taskKey;

        log.debug("Attempting to acquire lock '{}' (timeout={}s, lease={}s)",
                lockKey,
                props.getLock().getTimeoutSeconds(),
                props.getLock().getLeaseSeconds());

        // Obtain an RLock handle. This does NOT yet acquire the lock in Redis —
        // it only creates a local object that knows the key name.
        RLock lock = redissonClient.getLock(lockKey);

        long startMs = System.currentTimeMillis();

        try {
            // tryLock arguments:
            //   waitTime   — how long to wait for the lock (blocks/polls Redis)
            //   leaseTime  — how long to hold the lock (Redis key TTL)
            //   unit       — time unit for both values
            //
            // If leaseTime > 0 the lock TTL is set explicitly (recommended for production).
            // If leaseTime = -1 the Redisson watch-dog keeps renewing the TTL every 10 s.
            boolean acquired = lock.tryLock(
                    props.getLock().getTimeoutSeconds(),
                    props.getLock().getLeaseSeconds(),
                    TimeUnit.SECONDS
            );

            if (!acquired) {
                // Another node holds the lock. Return immediately instead of waiting.
                long elapsed = System.currentTimeMillis() - startMs;
                log.info("Lock '{}' not acquired — task SKIPPED (elapsed={}ms)", lockKey, elapsed);
                return TaskResult.skipped(taskKey, payload, elapsed);
            }

            log.info("Lock '{}' ACQUIRED — starting task processing", lockKey);

            try {
                // Simulate a long-running operation (e.g. generating a report,
                // sending an email, or running a database migration).
                // The configurable sleep duration makes lock contention visible
                // when sending concurrent HTTP requests during a demo.
                processTask(taskKey, payload);

                long elapsed = System.currentTimeMillis() - startMs;
                log.info("Lock '{}' — task COMPLETED in {}ms", lockKey, elapsed);
                return TaskResult.completed(taskKey, payload, elapsed);

            } finally {
                // CRITICAL: always release the lock in a finally block.
                // This ensures the lock is released even if processTask() throws
                // a RuntimeException, preventing dead-locks.
                //
                // isHeldByCurrentThread() guards against releasing a lock that
                // expired (leaseTime ran out) while we were still processing.
                // In that case the lock might already be held by another thread.
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("Lock '{}' released", lockKey);
                } else {
                    log.warn("Lock '{}' expired before unlock — lease time may be too short", lockKey);
                }
            }

        } catch (InterruptedException e) {
            // The thread was interrupted while waiting for the lock.
            // Restore the interrupt flag so callers can detect it.
            Thread.currentThread().interrupt();
            long elapsed = System.currentTimeMillis() - startMs;
            log.warn("Lock '{}' — thread interrupted (elapsed={}ms)", lockKey, elapsed);
            return TaskResult.interrupted(taskKey, payload, elapsed);
        }
    }

    /**
     * Simulates task processing by sleeping for the configured duration.
     *
     * <p>In a real application this would be replaced with actual business logic
     * such as: generating a report, sending emails, running a batch job, etc.
     * The sleep makes lock contention observable when running concurrent requests.
     *
     * @param taskKey the task identifier (used only for logging)
     * @param payload the data to process (logged to confirm the right payload is handled)
     * @throws InterruptedException if the thread is interrupted during processing
     */
    void processTask(String taskKey, String payload) throws InterruptedException {
        log.debug("Processing task '{}' with payload '{}' (simulating {}ms of work)",
                taskKey, payload, props.getTask().getProcessingMs());
        Thread.sleep(props.getTask().getProcessingMs());
        log.debug("Task '{}' processing complete", taskKey);
    }
}
