package com.example.multitenancy.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TenantConnectionProvider#toSchemaName(String)}.
 *
 * <p>The schema-name conversion is a critical security boundary: tenant IDs come
 * from HTTP headers (user-supplied input) and are embedded in a SQL statement
 * ({@code SET search_path TO ...}) that cannot use JDBC parameter binding.
 * These tests verify that the sanitization logic correctly rejects malicious input
 * and correctly normalizes valid input.</p>
 */
@DisplayName("TenantConnectionProvider — schema name validation and conversion")
class TenantConnectionProviderTest {

    @Test
    @DisplayName("should convert valid alphanumeric tenant ID to lowercase schema name")
    void shouldConvertValidAlphanumericId() {
        assertThat(TenantConnectionProvider.toSchemaName("tenant_alpha"))
                .isEqualTo("tenant_alpha");
    }

    @Test
    @DisplayName("should lowercase the tenant ID")
    void shouldLowercaseTenantId() {
        assertThat(TenantConnectionProvider.toSchemaName("TENANT_ALPHA"))
                .isEqualTo("tenant_alpha");
    }

    @Test
    @DisplayName("should accept mixed-case alphanumeric with underscores")
    void shouldAcceptMixedCaseWithUnderscores() {
        assertThat(TenantConnectionProvider.toSchemaName("Tenant_Beta_2"))
                .isEqualTo("tenant_beta_2");
    }

    @Test
    @DisplayName("should accept purely numeric tenant IDs")
    void shouldAcceptPurelyNumericId() {
        assertThat(TenantConnectionProvider.toSchemaName("12345"))
                .isEqualTo("12345");
    }

    @Test
    @DisplayName("should reject tenant ID containing a hyphen (SQL injection vector)")
    void shouldRejectTenantIdWithHyphen() {
        // Hyphens are not allowed — they are not valid unquoted PostgreSQL identifiers
        // and could be part of SQL injection attempts like "public; DROP TABLE products--"
        assertThatThrownBy(() -> TenantConnectionProvider.toSchemaName("tenant-alpha"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant identifier");
    }

    @Test
    @DisplayName("should reject tenant ID with SQL injection payload (semicolon)")
    void shouldRejectSqlInjectionWithSemicolon() {
        assertThatThrownBy(() -> TenantConnectionProvider.toSchemaName("public; DROP TABLE products--"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant identifier");
    }

    @Test
    @DisplayName("should reject tenant ID with SQL injection payload (quotes)")
    void shouldRejectSqlInjectionWithQuotes() {
        assertThatThrownBy(() -> TenantConnectionProvider.toSchemaName("tenant' OR '1'='1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant identifier");
    }

    @Test
    @DisplayName("should reject tenant ID with whitespace")
    void shouldRejectTenantIdWithWhitespace() {
        assertThatThrownBy(() -> TenantConnectionProvider.toSchemaName("tenant alpha"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant identifier");
    }

    @Test
    @DisplayName("should reject tenant ID with a dot")
    void shouldRejectTenantIdWithDot() {
        assertThatThrownBy(() -> TenantConnectionProvider.toSchemaName("schema.table"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant identifier");
    }

    @Test
    @DisplayName("should reject empty string as tenant ID")
    void shouldRejectEmptyString() {
        // An empty string does not match [a-zA-Z0-9_]+ (requires at least one character)
        assertThatThrownBy(() -> TenantConnectionProvider.toSchemaName(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant identifier");
    }
}
