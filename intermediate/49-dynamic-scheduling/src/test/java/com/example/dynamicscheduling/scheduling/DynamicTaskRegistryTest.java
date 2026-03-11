package com.example.dynamicscheduling.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DynamicTaskRegistry}.
 *
 * <p>These tests exercise the in-memory registry in complete isolation – no
 * Spring context, no database, no thread pool.  Each test creates a fresh
 * registry instance via {@code @BeforeEach} to ensure no state leaks between
 * test methods.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Interval registration and retrieval.</li>
 *   <li>Default fallback values when no entry exists.</li>
 *   <li>Enabled / disabled flag management.</li>
 *   <li>Future cancellation behaviour.</li>
 *   <li>Contains / remove operations.</li>
 * </ul>
 */
@DisplayName("DynamicTaskRegistry – unit tests")
class DynamicTaskRegistryTest {

    /** The registry under test – re-created fresh before every test. */
    private DynamicTaskRegistry registry;

    @BeforeEach
    void setUp() {
        // Create a brand-new registry for each test so no state leaks across tests
        registry = new DynamicTaskRegistry();
    }

    // ── Interval management ───────────────────────────────────────────────────────

    @Test
    @DisplayName("setInterval + getInterval – returns stored value")
    void setInterval_thenGetInterval_returnsStoredValue() {
        // Given: no entry in the registry yet

        // When: we register an interval
        registry.setInterval("my-task", 5000L);

        // Then: getInterval returns the stored value (default unused)
        assertThat(registry.getInterval("my-task", 9999L)).isEqualTo(5000L);
    }

    @Test
    @DisplayName("getInterval – returns default when task is not registered")
    void getInterval_taskNotRegistered_returnsDefault() {
        // Given: empty registry

        // When / Then: unknown task returns the provided default
        assertThat(registry.getInterval("unknown", 7000L)).isEqualTo(7000L);
    }

    @Test
    @DisplayName("setInterval twice – overrides the previous value")
    void setInterval_calledTwice_overridesPreviousValue() {
        // Given: initial interval registered
        registry.setInterval("task-a", 3000L);

        // When: interval is updated
        registry.setInterval("task-a", 10000L);

        // Then: latest value is returned
        assertThat(registry.getInterval("task-a", 0L)).isEqualTo(10000L);
    }

    // ── Enabled flag management ───────────────────────────────────────────────────

    @Test
    @DisplayName("setEnabled(true) – isEnabled returns true")
    void setEnabled_true_isEnabledReturnsTrue() {
        registry.setEnabled("task-b", true);
        assertThat(registry.isEnabled("task-b", false)).isTrue();
    }

    @Test
    @DisplayName("setEnabled(false) – isEnabled returns false")
    void setEnabled_false_isEnabledReturnsFalse() {
        registry.setEnabled("task-c", false);
        assertThat(registry.isEnabled("task-c", true)).isFalse();
    }

    @Test
    @DisplayName("isEnabled – returns default when task is not registered")
    void isEnabled_taskNotRegistered_returnsDefault() {
        // Given: empty registry

        // When / Then: default=true is returned for unknown task
        assertThat(registry.isEnabled("ghost-task", true)).isTrue();
        // And default=false is returned for unknown task
        assertThat(registry.isEnabled("ghost-task-2", false)).isFalse();
    }

    // ── Future management ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerFuture + cancelFuture – cancel() is called on the future")
    void registerFuture_thenCancelFuture_cancelsTheFuture() {
        // Given: a mock ScheduledFuture that is not yet done
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
        when(mockFuture.isDone()).thenReturn(false);

        registry.registerFuture("task-d", mockFuture);

        // When: the future is cancelled via the registry
        registry.cancelFuture("task-d");

        // Then: cancel(false) was invoked on the future (false = don't interrupt running execution)
        verify(mockFuture).cancel(false);
    }

    @Test
    @DisplayName("cancelFuture – no-op when task has no registered future")
    void cancelFuture_noRegisteredFuture_doesNotThrow() {
        // Given: empty registry

        // When / Then: cancelling an unknown task must not throw
        // (this tests the null-safety guard inside cancelFuture)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
            () -> registry.cancelFuture("non-existent")
        );
    }

    @Test
    @DisplayName("cancelFuture – no-op when future is already done")
    void cancelFuture_futureDone_doesNotCallCancel() {
        // Given: a mock future that reports itself as already completed
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> doneFuture = mock(ScheduledFuture.class);
        when(doneFuture.isDone()).thenReturn(true);

        registry.registerFuture("task-e", doneFuture);

        // When: cancelFuture is called
        registry.cancelFuture("task-e");

        // Then: cancel() should NOT be called on an already-done future
        verify(doneFuture, never()).cancel(anyBoolean());
    }

    // ── Contains / Remove ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("contains – returns false for unregistered task")
    void contains_unregisteredTask_returnsFalse() {
        assertThat(registry.contains("unregistered")).isFalse();
    }

    @Test
    @DisplayName("contains – returns true after setInterval is called")
    void contains_afterSetInterval_returnsTrue() {
        registry.setInterval("task-f", 1000L);
        assertThat(registry.contains("task-f")).isTrue();
    }

    @Test
    @DisplayName("remove – clears interval, enabled flag, and future")
    void remove_clearAllRegistryState() {
        // Given: a fully registered task with a mock future
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
        when(mockFuture.isDone()).thenReturn(false);

        registry.setInterval("task-g", 2000L);
        registry.setEnabled("task-g", true);
        registry.registerFuture("task-g", mockFuture);

        // When: the task is removed
        registry.remove("task-g");

        // Then: the task is no longer in the registry
        assertThat(registry.contains("task-g")).isFalse();

        // And: interval falls back to default
        assertThat(registry.getInterval("task-g", 42L)).isEqualTo(42L);

        // And: enabled falls back to default
        assertThat(registry.isEnabled("task-g", false)).isFalse();

        // And: the future was cancelled
        verify(mockFuture).cancel(false);
    }

    @Test
    @DisplayName("getRegisteredTaskNames – returns all registered task names")
    void getRegisteredTaskNames_returnsAllNames() {
        registry.setInterval("alpha", 1000L);
        registry.setInterval("beta",  2000L);
        registry.setInterval("gamma", 3000L);

        assertThat(registry.getRegisteredTaskNames())
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }
}
