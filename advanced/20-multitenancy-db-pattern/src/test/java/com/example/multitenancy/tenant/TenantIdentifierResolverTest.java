package com.example.multitenancy.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantIdentifierResolver}.
 *
 * <p>This resolver is the Hibernate SPI hook that translates the thread-local
 * tenant context into the tenant identifier Hibernate uses when opening sessions.
 * Tests verify the fallback to the default tenant when no context is set.</p>
 */
@DisplayName("TenantIdentifierResolver — resolves tenant from TenantContext")
class TenantIdentifierResolverTest {

    private final TenantIdentifierResolver resolver = new TenantIdentifierResolver();

    /**
     * Always clean up the thread-local after each test.
     */
    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("should resolve the tenant ID from TenantContext when one is set")
    void shouldResolveTenantFromContext() {
        TenantContext.setCurrentTenant("tenant_alpha");

        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("tenant_alpha");
    }

    @Test
    @DisplayName("should return default tenant when TenantContext is empty")
    void shouldReturnDefaultTenantWhenContextEmpty() {
        // No tenant set — context is null
        assertThat(TenantContext.getCurrentTenant()).isNull();

        // Resolver should fall back to the default value
        assertThat(resolver.resolveCurrentTenantIdentifier())
                .isEqualTo(TenantIdentifierResolver.DEFAULT_TENANT);
    }

    @Test
    @DisplayName("should return default tenant when TenantContext contains blank string")
    void shouldReturnDefaultTenantWhenContextBlank() {
        TenantContext.setCurrentTenant("   "); // blank, not null

        assertThat(resolver.resolveCurrentTenantIdentifier())
                .isEqualTo(TenantIdentifierResolver.DEFAULT_TENANT);
    }

    @Test
    @DisplayName("validateExistingCurrentSessions() should return true")
    void validateExistingCurrentSessionsShouldReturnTrue() {
        assertThat(resolver.validateExistingCurrentSessions()).isTrue();
    }
}
