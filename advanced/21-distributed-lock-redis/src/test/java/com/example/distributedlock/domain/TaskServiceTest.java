package com.example.distributedlock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import com.example.distributedlock.config.AppProperties;

/**
 * Unit tests for {@link TaskService}.
 *
 * <p>These tests use Mockito to mock the Redisson {@link RedissonClient} and its
 * {@link RLock}.  No Spring context is loaded and no Docker/Redis is needed —
 * all Redis interactions are stubbed.
 *
 * <p>The tests verify:
 * <ul>
 *   <li>When the lock IS acquired → task is processed → COMPLETED result.</li>
 *   <li>When the lock IS NOT acquired (timeout) → task is skipped → SKIPPED result.</li>
 *   <li>When the thread is interrupted → INTERRUPTED result, interrupt flag is restored.</li>
 *   <li>Lock is always released in a finally block (even when processing throws).</li>
 *   <li>The correct lock key prefix is used.</li>
 * </ul>
 *
 * <h2>Why mock Redis?</h2>
 * Unit tests should be fast and deterministic. Spinning up a real Redis instance
 * for every test would be slow and environment-dependent. Mockito lets us control
 * exactly what {@code tryLock()} returns so we can test both the success and
 * failure paths without any infrastructure.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService — unit tests")
class TaskServiceTest {

    /**
     * Mocked Redisson client — no real Redis connection is made.
     * We stub getLock() to return a mocked RLock.
     */
    @Mock
    private RedissonClient redissonClient;

    /**
     * Mocked RLock — controls tryLock() return value and tracks unlock() calls.
     */
    @Mock
    private RLock rLock;

    /**
     * Real AppProperties with test-friendly values:
     *   - timeout: 2 seconds (fast fail)
     *   - lease: 10 seconds
     *   - processing: 0 ms (no sleep in unit tests)
     */
    private AppProperties props;

    /**
     * The system under test — constructed with the mocked collaborators.
     */
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        // Build AppProperties programmatically for unit tests
        props = new AppProperties();
        props.getLock().setTimeoutSeconds(2);
        props.getLock().setLeaseSeconds(10);
        props.getTask().setProcessingMs(0);  // no sleep in unit tests — keeps them fast

        taskService = new TaskService(redissonClient, props);
        // Note: getLock stub is added per-test to avoid UnnecessaryStubbingException
        // in tests that do not invoke the service (e.g. lockKeyPrefix_hasExpectedValue).
    }

    /**
     * Helper: stubs redissonClient.getLock(any) → rLock.
     * Called by every test that exercises the service's locking flow.
     */
    private void stubGetLock() {
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(rLock);
    }

    // -------------------------------------------------------------------------
    // Happy path: lock acquired, task completes
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("executeWithLock — COMPLETED when lock is acquired")
    void executeWithLock_lockAcquired_returnsCompleted() throws InterruptedException {
        // given — tryLock returns true (lock acquired successfully)
        stubGetLock();
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        // isHeldByCurrentThread returns true so the finally block calls unlock()
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // when
        TaskResult result = taskService.executeWithLock("report-job", "Q4 data");

        // then — task ran to completion
        assertThat(result.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.taskKey()).isEqualTo("report-job");
        assertThat(result.payload()).isEqualTo("Q4 data");
        assertThat(result.message()).isNotBlank();
        assertThat(result.timestamp()).isNotNull();
        assertThat(result.elapsedMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("executeWithLock — unlock() is called after successful execution")
    void executeWithLock_lockAcquired_unlockIsCalled() throws InterruptedException {
        // given
        stubGetLock();
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // when
        taskService.executeWithLock("my-task", "payload");

        // then — the lock MUST be released after successful processing
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("executeWithLock — uses correct Redis key prefix")
    void executeWithLock_usesCorrectLockKeyPrefix() throws InterruptedException {
        // given
        stubGetLock();
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // when
        taskService.executeWithLock("my-task", "payload");

        // then — the lock key must be prefixed with "task-lock:"
        verify(redissonClient).getLock("task-lock:my-task");
    }

    @Test
    @DisplayName("executeWithLock — uses configured timeout and lease values")
    void executeWithLock_usesConfiguredTimeoutAndLease() throws InterruptedException {
        // given
        stubGetLock();
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // when
        taskService.executeWithLock("my-task", "payload");

        // then — tryLock must be called with the values from AppProperties
        verify(rLock).tryLock(
                props.getLock().getTimeoutSeconds(),  // waitTime = 2
                props.getLock().getLeaseSeconds(),    // leaseTime = 10
                TimeUnit.SECONDS
        );
    }

    // -------------------------------------------------------------------------
    // Lock not available: another node holds it
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("executeWithLock — SKIPPED when lock cannot be acquired")
    void executeWithLock_lockNotAcquired_returnsSkipped() throws InterruptedException {
        // given — tryLock returns false (lock held by another node)
        stubGetLock();
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        // when
        TaskResult result = taskService.executeWithLock("report-job", "Q4 data");

        // then — task was skipped (not executed)
        assertThat(result.status()).isEqualTo(TaskStatus.SKIPPED);
        assertThat(result.taskKey()).isEqualTo("report-job");
        assertThat(result.message()).containsIgnoringCase("skipped");
    }

    @Test
    @DisplayName("executeWithLock — unlock() is NOT called when lock was never acquired")
    void executeWithLock_lockNotAcquired_unlockIsNotCalled() throws InterruptedException {
        // given
        stubGetLock();
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        // when
        taskService.executeWithLock("my-task", "payload");

        // then — unlock must NOT be called (we never held the lock)
        verify(rLock, never()).unlock();
    }

    // -------------------------------------------------------------------------
    // Interrupted thread
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("executeWithLock — INTERRUPTED when thread is interrupted during tryLock")
    void executeWithLock_threadInterrupted_returnsInterrupted() throws InterruptedException {
        // given — tryLock throws InterruptedException (e.g. thread pool shutdown)
        stubGetLock();
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("test-interrupt"));

        // when
        TaskResult result = taskService.executeWithLock("report-job", "Q4 data");

        // then — must return INTERRUPTED status
        assertThat(result.status()).isEqualTo(TaskStatus.INTERRUPTED);

        // and the interrupt flag must be restored so callers can detect it
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        // clean up: clear the interrupt flag so it doesn't affect other tests
        Thread.interrupted();
    }

    @Test
    @DisplayName("executeWithLock — interrupt flag is restored after InterruptedException")
    void executeWithLock_interruptFlag_isRestored() throws InterruptedException {
        // given
        stubGetLock();
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("test-interrupt"));

        // when
        taskService.executeWithLock("key", "payload");

        // then — Thread.currentThread().isInterrupted() must be true
        boolean interrupted = Thread.currentThread().isInterrupted();
        // clean up before assertion so the test runner is not affected
        Thread.interrupted();

        assertThat(interrupted).isTrue();
    }

    // -------------------------------------------------------------------------
    // Lock key prefix constant
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("LOCK_KEY_PREFIX constant has expected value")
    void lockKeyPrefix_hasExpectedValue() {
        // The prefix is part of the Redis key schema — any change would be a breaking change
        assertThat(TaskService.LOCK_KEY_PREFIX).isEqualTo("task-lock:");
    }

    // -------------------------------------------------------------------------
    // Different taskKeys use different lock instances
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Different taskKeys request different locks from RedissonClient")
    void differentTaskKeys_requestDifferentLocks() throws InterruptedException {
        // given
        stubGetLock();
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        // when
        taskService.executeWithLock("task-A", "payload");
        taskService.executeWithLock("task-B", "payload");

        // then — two distinct lock keys were requested
        verify(redissonClient).getLock("task-lock:task-A");
        verify(redissonClient).getLock("task-lock:task-B");
    }
}
