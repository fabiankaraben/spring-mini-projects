package com.example.multitenancy.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantContext}.
 *
 * <p>These tests verify the thread-local lifecycle: set, get, and clear. They
 * run without any Spring context — pure JUnit 5 + AssertJ.</p>
 */
@DisplayName("TenantContext — thread-local lifecycle")
class TenantContextTest {

    /**
     * Always clear the thread-local after each test to prevent leakage between
     * test methods (JUnit may reuse threads in parallel execution).
     */
    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("should return null when no tenant has been set")
    void shouldReturnNullWhenNoTenantSet() {
        // No setup — context should be empty at the start of each test
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("should return the tenant ID after setCurrentTenant()")
    void shouldReturnTenantIdAfterSet() {
        TenantContext.setCurrentTenant("tenant_alpha");

        assertThat(TenantContext.getCurrentTenant()).isEqualTo("tenant_alpha");
    }

    @Test
    @DisplayName("should overwrite the tenant ID when setCurrentTenant() is called twice")
    void shouldOverwriteTenantIdOnSecondSet() {
        TenantContext.setCurrentTenant("tenant_alpha");
        TenantContext.setCurrentTenant("tenant_beta");

        assertThat(TenantContext.getCurrentTenant()).isEqualTo("tenant_beta");
    }

    @Test
    @DisplayName("should return null after clear()")
    void shouldReturnNullAfterClear() {
        TenantContext.setCurrentTenant("tenant_alpha");
        TenantContext.clear();

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("clear() should be idempotent (safe to call when context is already empty)")
    void clearShouldBeIdempotent() {
        // clear() on an empty context must not throw
        TenantContext.clear();
        TenantContext.clear();

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("should isolate tenant IDs across different threads")
    void shouldIsolateTenantAcrossThreads() throws InterruptedException {
        // Set a tenant in the main thread
        TenantContext.setCurrentTenant("main_thread_tenant");

        // Track what the child thread sees in its own ThreadLocal
        String[] childThreadTenant = new String[1];

        Thread childThread = new Thread(() -> {
            // The child thread should NOT inherit the parent thread's tenant
            // (we use ThreadLocal, not InheritableThreadLocal)
            childThreadTenant[0] = TenantContext.getCurrentTenant();
        });

        childThread.start();
        childThread.join();

        // Main thread still has its tenant
        assertThat(TenantContext.getCurrentTenant()).isEqualTo("main_thread_tenant");

        // Child thread had no tenant (ThreadLocal isolation confirmed)
        assertThat(childThreadTenant[0]).isNull();
    }
}
