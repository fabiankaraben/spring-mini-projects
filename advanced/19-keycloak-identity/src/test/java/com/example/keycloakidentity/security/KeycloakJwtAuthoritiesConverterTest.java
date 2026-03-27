package com.example.keycloakidentity.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KeycloakJwtAuthoritiesConverter}.
 *
 * <p>These tests verify that the converter correctly extracts Spring Security
 * {@link GrantedAuthority} objects from Keycloak-issued JWT claims.
 *
 * <p><b>Testing approach:</b>
 * <ul>
 *   <li>Tests use JUnit 5 and AssertJ only — no Spring context is loaded.</li>
 *   <li>JWT objects are constructed via {@link Jwt#withTokenValue(String)} builder,
 *       which lets us specify any claims we want without a real Keycloak server.</li>
 *   <li>Nested test classes group tests by method under test for clarity.</li>
 * </ul>
 */
@DisplayName("KeycloakJwtAuthoritiesConverter — unit tests")
class KeycloakJwtAuthoritiesConverterTest {

    /**
     * Converter under test (no resource ID — realm roles + scopes only).
     */
    private KeycloakJwtAuthoritiesConverter converter;

    @BeforeEach
    void setUp() {
        // Use the no-args constructor: reads realm_access.roles + scope claims
        converter = new KeycloakJwtAuthoritiesConverter();
    }

    // =========================================================================
    // Helper method to build test JWTs
    // =========================================================================

    /**
     * Builds a minimal Keycloak-style JWT with the given claims for testing.
     *
     * @param claims the JWT claims to include
     * @return a Jwt object representing the test token
     */
    private Jwt buildJwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("test-token-value")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .issuer("http://localhost:9080/realms/demo-realm")
                .subject("test-user-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(claims))
                .build();
    }

    // =========================================================================
    // Realm roles extraction (realm_access.roles)
    // =========================================================================

    @Nested
    @DisplayName("realm_access.roles extraction")
    class RealmRolesTests {

        /**
         * Verifies that USER role in realm_access.roles becomes ROLE_USER authority.
         */
        @Test
        @DisplayName("maps USER role to ROLE_USER authority")
        void mapsUserRoleToRoleUserAuthority() {
            Jwt jwt = buildJwt(Map.of(
                    "realm_access", Map.of("roles", List.of("USER"))
            ));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .contains("ROLE_USER");
        }

        /**
         * Verifies that ADMIN role in realm_access.roles becomes ROLE_ADMIN authority.
         */
        @Test
        @DisplayName("maps ADMIN role to ROLE_ADMIN authority")
        void mapsAdminRoleToRoleAdminAuthority() {
            Jwt jwt = buildJwt(Map.of(
                    "realm_access", Map.of("roles", List.of("ADMIN"))
            ));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .contains("ROLE_ADMIN");
        }

        /**
         * Verifies that multiple realm roles are all converted.
         */
        @Test
        @DisplayName("maps multiple realm roles to ROLE_* authorities")
        void mapsMultipleRealmRoles() {
            Jwt jwt = buildJwt(Map.of(
                    "realm_access", Map.of("roles", List.of("USER", "ADMIN", "offline_access"))
            ));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN", "ROLE_offline_access");
        }

        /**
         * Verifies that a JWT with no realm_access claim produces no role authorities.
         */
        @Test
        @DisplayName("produces no role authorities when realm_access claim is absent")
        void producesNoRoleAuthoritiesWhenRealmAccessAbsent() {
            // JWT with only a scope claim — no realm_access
            Jwt jwt = buildJwt(Map.of("scope", "openid"));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            // Should only have scope authorities, no ROLE_* ones
            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .noneMatch(a -> a.startsWith("ROLE_"));
        }

        /**
         * Verifies that a JWT with realm_access but empty roles list produces no role authorities.
         */
        @Test
        @DisplayName("produces no role authorities when realm_access.roles is empty")
        void producesNoRoleAuthoritiesWhenRolesEmpty() {
            Jwt jwt = buildJwt(Map.of(
                    "realm_access", Map.of("roles", List.of())
            ));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .noneMatch(a -> a.startsWith("ROLE_"));
        }
    }

    // =========================================================================
    // Scope extraction (scope claim)
    // =========================================================================

    @Nested
    @DisplayName("scope claim extraction")
    class ScopeTests {

        /**
         * Verifies that each space-separated scope token becomes a SCOPE_* authority.
         */
        @Test
        @DisplayName("maps space-separated scopes to SCOPE_* authorities")
        void mapsSpaceSeparatedScopesToScopeAuthorities() {
            Jwt jwt = buildJwt(Map.of("scope", "openid email profile"));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .contains("SCOPE_openid", "SCOPE_email", "SCOPE_profile");
        }

        /**
         * Verifies that a single scope produces a single SCOPE_* authority.
         */
        @Test
        @DisplayName("maps single scope to one SCOPE_* authority")
        void mapsSingleScopeToOneScopeAuthority() {
            Jwt jwt = buildJwt(Map.of("scope", "openid"));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .contains("SCOPE_openid");
        }

        /**
         * Verifies that a JWT with no scope claim produces no scope authorities.
         */
        @Test
        @DisplayName("produces no scope authorities when scope claim is absent")
        void producesNoScopeAuthoritiesWhenScopeAbsent() {
            Jwt jwt = buildJwt(Map.of(
                    "realm_access", Map.of("roles", List.of("USER"))
            ));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .noneMatch(a -> a.startsWith("SCOPE_"));
        }
    }

    // =========================================================================
    // Combined claims
    // =========================================================================

    @Nested
    @DisplayName("combined claims")
    class CombinedClaimsTests {

        /**
         * Verifies that both realm roles AND scopes are extracted from a full Keycloak JWT.
         */
        @Test
        @DisplayName("extracts both ROLE_* and SCOPE_* authorities from full Keycloak JWT")
        void extractsBothRolesAndScopes() {
            Jwt jwt = buildJwt(Map.of(
                    "realm_access", Map.of("roles", List.of("USER", "ADMIN")),
                    "scope", "openid email profile"
            ));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder(
                            "ROLE_USER", "ROLE_ADMIN",
                            "SCOPE_openid", "SCOPE_email", "SCOPE_profile"
                    );
        }

        /**
         * Verifies that a completely empty JWT produces an empty authorities collection.
         */
        @Test
        @DisplayName("returns empty collection for JWT with no relevant claims")
        void returnsEmptyCollectionForJwtWithNoRelevantClaims() {
            // JWT with only standard claims, no realm_access or scope
            Jwt jwt = buildJwt(Map.of());

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).isEmpty();
        }
    }

    // =========================================================================
    // Client roles (resource_access) extraction with resourceId configured
    // =========================================================================

    @Nested
    @DisplayName("resource_access roles extraction (with resourceId)")
    class ResourceAccessTests {

        /**
         * Verifies that client-specific roles from resource_access are extracted
         * when a resourceId is configured in the converter.
         */
        @Test
        @DisplayName("extracts client roles from resource_access when resourceId is configured")
        void extractsClientRolesWhenResourceIdConfigured() {
            // Use the constructor that accepts a resourceId
            KeycloakJwtAuthoritiesConverter converterWithClient =
                    new KeycloakJwtAuthoritiesConverter("my-client");

            Jwt jwt = buildJwt(Map.of(
                    "resource_access", Map.of(
                            "my-client", Map.of("roles", List.of("client-admin"))
                    )
            ));

            Collection<GrantedAuthority> authorities = converterWithClient.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .contains("ROLE_client-admin");
        }

        /**
         * Verifies that client roles for OTHER clients are not extracted.
         */
        @Test
        @DisplayName("ignores client roles for other client IDs")
        void ignoresClientRolesForOtherClientIds() {
            KeycloakJwtAuthoritiesConverter converterWithClient =
                    new KeycloakJwtAuthoritiesConverter("my-client");

            Jwt jwt = buildJwt(Map.of(
                    "resource_access", Map.of(
                            "other-client", Map.of("roles", List.of("other-role"))
                    )
            ));

            Collection<GrantedAuthority> authorities = converterWithClient.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .doesNotContain("ROLE_other-role");
        }

        /**
         * Verifies that without resourceId, resource_access claims are ignored entirely.
         */
        @Test
        @DisplayName("ignores resource_access when no resourceId is configured")
        void ignoresResourceAccessWhenNoResourceIdConfigured() {
            // No-args constructor — resourceId is null
            KeycloakJwtAuthoritiesConverter converterNoClient =
                    new KeycloakJwtAuthoritiesConverter();

            Jwt jwt = buildJwt(Map.of(
                    "resource_access", Map.of(
                            "my-client", Map.of("roles", List.of("client-role"))
                    )
            ));

            Collection<GrantedAuthority> authorities = converterNoClient.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .doesNotContain("ROLE_client-role");
        }
    }
}
