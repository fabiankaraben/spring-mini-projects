package com.example.authserver.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CustomTokenCustomizer} — the JWT claims enricher.
 *
 * <p>These tests verify the <em>pure domain logic</em> of the customizer:
 * scope-to-role mapping, tenant derivation, and metadata construction.
 * No Spring context is loaded — these tests run as plain JUnit 5 tests
 * using AssertJ for fluent assertions.
 *
 * <p><b>What we test here:</b>
 * <ul>
 *   <li>{@link CustomTokenCustomizer#mapScopesToRoles(Set)} — scope → ROLE_* mapping</li>
 *   <li>{@link CustomTokenCustomizer#deriveTenant(String)} — clientId → tenant string</li>
 *   <li>{@link CustomTokenCustomizer#buildMetadata(String, Set)} — metadata map content</li>
 * </ul>
 *
 * <p><b>Why unit-test the customizer separately?</b>
 * The {@code customize()} method is called deep inside Spring Authorization Server's
 * token issuance pipeline. Testing the individual helper methods in isolation lets
 * us verify the logic quickly without needing a running server or database.
 * The integration test separately verifies that the claims actually appear in
 * real issued JWTs.
 */
@DisplayName("CustomTokenCustomizer — unit tests")
class CustomTokenCustomizerTest {

    /**
     * The class under test. Instantiated directly — no Spring DI needed
     * because CustomTokenCustomizer has no dependencies.
     */
    private CustomTokenCustomizer customizer;

    /**
     * Initialises a fresh instance of the customizer before each test.
     * This ensures test isolation — no shared mutable state between tests.
     */
    @BeforeEach
    void setUp() {
        customizer = new CustomTokenCustomizer();
    }

    // =========================================================================
    // Tests for mapScopesToRoles()
    // =========================================================================

    @Nested
    @DisplayName("mapScopesToRoles()")
    class MapScopesToRolesTests {

        /**
         * Verifies that the "read" scope maps to "ROLE_READER".
         */
        @Test
        @DisplayName("read scope maps to ROLE_READER")
        void readScopeMapsToRoleReader() {
            List<String> roles = customizer.mapScopesToRoles(Set.of("read"));

            assertThat(roles).containsExactly("ROLE_READER");
        }

        /**
         * Verifies that the "write" scope maps to "ROLE_WRITER".
         */
        @Test
        @DisplayName("write scope maps to ROLE_WRITER")
        void writeScopeMapsToRoleWriter() {
            List<String> roles = customizer.mapScopesToRoles(Set.of("write"));

            assertThat(roles).containsExactly("ROLE_WRITER");
        }

        /**
         * Verifies that the "api.read" scope maps to "ROLE_API_READER".
         */
        @Test
        @DisplayName("api.read scope maps to ROLE_API_READER")
        void apiReadScopeMapsToRoleApiReader() {
            List<String> roles = customizer.mapScopesToRoles(Set.of("api.read"));

            assertThat(roles).containsExactly("ROLE_API_READER");
        }

        /**
         * Verifies that "openid" and "profile" (OIDC scopes) are filtered out
         * and do not produce any role entries.
         */
        @Test
        @DisplayName("openid and profile scopes are filtered out (no roles)")
        void oidcScopesAreFilteredOut() {
            List<String> roles = customizer.mapScopesToRoles(Set.of("openid", "profile"));

            assertThat(roles).isEmpty();
        }

        /**
         * Verifies that multiple scopes all produce their respective roles,
         * including OIDC scopes being correctly filtered out.
         */
        @Test
        @DisplayName("multiple scopes produce all non-OIDC roles, sorted")
        void multipleScopesMappedAndSorted() {
            List<String> roles = customizer.mapScopesToRoles(
                    Set.of("openid", "profile", "read", "write"));

            // Sorted alphabetically: ROLE_READER before ROLE_WRITER
            assertThat(roles)
                    .hasSize(2)
                    .contains("ROLE_READER", "ROLE_WRITER");
            // Result must be sorted (test is order-sensitive via isSorted)
            assertThat(roles).isSorted();
        }

        /**
         * Verifies that an empty scope set produces an empty role list.
         */
        @Test
        @DisplayName("empty scopes produce empty roles list")
        void emptyScopesProduceEmptyRoles() {
            List<String> roles = customizer.mapScopesToRoles(Set.of());

            assertThat(roles).isEmpty();
        }

        /**
         * Verifies that an unknown custom scope is mapped to
         * "ROLE_" + UPPERCASED_SCOPE_NAME (fallback convention).
         */
        @Test
        @DisplayName("unknown scope maps to ROLE_<UPPERCASE> via fallback")
        void unknownScopeUsedFallbackMapping() {
            List<String> roles = customizer.mapScopesToRoles(Set.of("admin"));

            assertThat(roles).containsExactly("ROLE_ADMIN");
        }

        /**
         * Verifies that a scope with a dot (e.g., "data.export") maps to
         * "ROLE_DATA_EXPORT" — dots are replaced with underscores.
         */
        @Test
        @DisplayName("scope with dot uses fallback and replaces dot with underscore")
        void scopeWithDotUsedFallbackWithUnderscore() {
            List<String> roles = customizer.mapScopesToRoles(Set.of("data.export"));

            assertThat(roles).containsExactly("ROLE_DATA_EXPORT");
        }
    }

    // =========================================================================
    // Tests for deriveTenant()
    // =========================================================================

    @Nested
    @DisplayName("deriveTenant()")
    class DeriveTenantTests {

        /**
         * Verifies that client IDs starting with "service-" map to tenant "internal".
         */
        @Test
        @DisplayName("clientId starting with 'service-' maps to tenant 'internal'")
        void serviceClientMapsToInternalTenant() {
            String tenant = customizer.deriveTenant("service-account-client");

            assertThat(tenant).isEqualTo("internal");
        }

        /**
         * Verifies that a regular client (not starting with "service-") maps to "default".
         */
        @Test
        @DisplayName("regular clientId maps to tenant 'default'")
        void regularClientMapsToDefaultTenant() {
            String tenant = customizer.deriveTenant("messaging-client");

            assertThat(tenant).isEqualTo("default");
        }

        /**
         * Verifies that a null clientId falls back to "default" tenant
         * without throwing a NullPointerException.
         */
        @Test
        @DisplayName("null clientId maps to tenant 'default' without NPE")
        void nullClientIdMapsToDefaultTenant() {
            String tenant = customizer.deriveTenant(null);

            assertThat(tenant).isEqualTo("default");
        }

        /**
         * Verifies that a clientId that is exactly "service-" (the prefix alone)
         * still maps to "internal" tenant.
         */
        @Test
        @DisplayName("clientId exactly 'service-' still maps to 'internal'")
        void bareServicePrefixMapsToInternalTenant() {
            String tenant = customizer.deriveTenant("service-");

            assertThat(tenant).isEqualTo("internal");
        }
    }

    // =========================================================================
    // Tests for buildMetadata()
    // =========================================================================

    @Nested
    @DisplayName("buildMetadata()")
    class BuildMetadataTests {

        /**
         * Verifies that the metadata map always includes the "client_id" key
         * with the correct value.
         */
        @Test
        @DisplayName("metadata always includes client_id key")
        void metadataIncludesClientId() {
            Map<String, Object> metadata = customizer.buildMetadata(
                    "messaging-client", Set.of("read"));

            assertThat(metadata).containsKey("client_id");
            assertThat(metadata.get("client_id")).isEqualTo("messaging-client");
        }

        /**
         * Verifies that service clients receive "premium" rate limit tier.
         */
        @Test
        @DisplayName("service client gets 'premium' rate_limit_tier")
        void serviceClientGetsPremiumRateLimitTier() {
            Map<String, Object> metadata = customizer.buildMetadata(
                    "service-account-client", Set.of("api.read"));

            assertThat(metadata.get("rate_limit_tier")).isEqualTo("premium");
        }

        /**
         * Verifies that regular clients receive "standard" rate limit tier.
         */
        @Test
        @DisplayName("regular client gets 'standard' rate_limit_tier")
        void regularClientGetsStandardRateLimitTier() {
            Map<String, Object> metadata = customizer.buildMetadata(
                    "messaging-client", Set.of("read", "write"));

            assertThat(metadata.get("rate_limit_tier")).isEqualTo("standard");
        }

        /**
         * Verifies that the metadata map includes the "allowed_scopes" key
         * as a non-empty string containing all granted scopes.
         */
        @Test
        @DisplayName("metadata includes allowed_scopes as comma-separated string")
        void metadataIncludesAllowedScopes() {
            Map<String, Object> metadata = customizer.buildMetadata(
                    "messaging-client", Set.of("read", "write"));

            assertThat(metadata).containsKey("allowed_scopes");
            String allowedScopes = (String) metadata.get("allowed_scopes");
            // The scopes must both appear; order depends on Set iteration
            assertThat(allowedScopes).contains("read");
            assertThat(allowedScopes).contains("write");
        }

        /**
         * Verifies that a null clientId is handled gracefully in metadata building.
         */
        @Test
        @DisplayName("null clientId does not throw NPE in metadata building")
        void nullClientIdHandledGracefully() {
            Map<String, Object> metadata = customizer.buildMetadata(null, Set.of("read"));

            // client_id key should be present but null value
            assertThat(metadata).containsKey("client_id");
            // rate_limit_tier falls back to "standard" for null client
            assertThat(metadata.get("rate_limit_tier")).isEqualTo("standard");
        }
    }
}
