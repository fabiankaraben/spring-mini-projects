package com.example.resourceserver.security;

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
 * Unit tests for {@link JwtClaimsAuthoritiesConverter}.
 *
 * <p>These tests verify the pure conversion logic in isolation — no Spring context
 * is loaded. A real {@link Jwt} object is constructed using the builder pattern
 * to simulate JWT payloads with various combinations of claims.
 *
 * <p><b>What we test:</b>
 * <ul>
 *   <li>Scope claim extraction → {@code SCOPE_*} authorities</li>
 *   <li>Roles claim extraction → direct {@code ROLE_*} authorities</li>
 *   <li>Combined scope + roles</li>
 *   <li>Fallback from {@code scope} to {@code scp} claim</li>
 *   <li>Empty / missing claims produce no authorities</li>
 * </ul>
 */
@DisplayName("JwtClaimsAuthoritiesConverter — unit tests")
class JwtClaimsAuthoritiesConverterTest {

    /**
     * The class under test. Instantiated directly — no Spring DI needed
     * because {@link JwtClaimsAuthoritiesConverter} has no dependencies.
     */
    private JwtClaimsAuthoritiesConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JwtClaimsAuthoritiesConverter();
    }

    // =========================================================================
    // Helper: build a Jwt with arbitrary claims
    // =========================================================================

    /**
     * Builds a minimal {@link Jwt} object for testing using the Jwt builder.
     *
     * <p>The Jwt class requires at minimum: tokenValue, issuedAt, expiresAt,
     * and headers. We use a fixed set of these for all tests.
     *
     * @param claims additional claims to add to the JWT payload
     * @return a Jwt instance with the given claims
     */
    private Jwt buildJwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }

    // =========================================================================
    // Scope claim tests
    // =========================================================================

    @Nested
    @DisplayName("scope claim → SCOPE_* authorities")
    class ScopeClaimTests {

        /**
         * Verifies that a space-separated "scope" claim produces one SCOPE_* authority
         * per scope token.
         *
         * <p>A JWT with {@code "scope": "products.read products.write"} should
         * produce {@code [SCOPE_products.read, SCOPE_products.write]}.
         */
        @Test
        @DisplayName("space-separated scope string produces SCOPE_* authorities")
        void scopeStringProducesScopeAuthorities() {
            Jwt jwt = buildJwt(Map.of("scope", "products.read products.write"));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("SCOPE_products.read", "SCOPE_products.write");
        }

        /**
         * Verifies that a single scope value produces exactly one SCOPE_* authority.
         */
        @Test
        @DisplayName("single scope produces one SCOPE_* authority")
        void singleScopeProducesOneAuthority() {
            Jwt jwt = buildJwt(Map.of("scope", "products.read"));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("SCOPE_products.read");
        }

        /**
         * Verifies that an empty scope string produces no authorities.
         * An empty scope means the token has no authorized operations.
         */
        @Test
        @DisplayName("blank scope string produces no authorities")
        void blankScopeProducesNoAuthorities() {
            Jwt jwt = buildJwt(Map.of("scope", "   "));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).isEmpty();
        }

        /**
         * Verifies that when "scope" is absent but "scp" is present, the "scp" claim
         * is used as a fallback.
         *
         * <p>Some authorization servers (e.g., Microsoft Azure AD) use "scp" instead
         * of the standard "scope" claim name.
         */
        @Test
        @DisplayName("falls back to 'scp' claim when 'scope' is absent")
        void fallsBackToScpClaim() {
            Jwt jwt = buildJwt(Map.of("scp", "products.read"));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("SCOPE_products.read");
        }
    }

    // =========================================================================
    // Roles claim tests
    // =========================================================================

    @Nested
    @DisplayName("roles claim → direct ROLE_* authorities")
    class RolesClaimTests {

        /**
         * Verifies that the custom "roles" claim list is converted to authorities directly.
         *
         * <p>The Authorization Server's token customizer adds a "roles" array.
         * Each value already carries the "ROLE_" prefix, so we use them as-is.
         */
        @Test
        @DisplayName("roles list produces authorities with ROLE_ prefix as-is")
        void rolesListProducesRoleAuthorities() {
            Jwt jwt = buildJwt(Map.of("roles", List.of("ROLE_READER", "ROLE_WRITER")));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_READER", "ROLE_WRITER");
        }

        /**
         * Verifies that a single-element roles list produces exactly one authority.
         */
        @Test
        @DisplayName("single role produces one authority")
        void singleRoleProducesOneAuthority() {
            Jwt jwt = buildJwt(Map.of("roles", List.of("ROLE_API_READER")));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_API_READER");
        }

        /**
         * Verifies that an empty roles list produces no role authorities.
         */
        @Test
        @DisplayName("empty roles list produces no role authorities")
        void emptyRolesListProducesNoAuthorities() {
            Jwt jwt = buildJwt(Map.of("roles", List.of()));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).isEmpty();
        }
    }

    // =========================================================================
    // Combined scope + roles tests
    // =========================================================================

    @Nested
    @DisplayName("combined scope and roles claims")
    class CombinedClaimsTests {

        /**
         * Verifies that when both "scope" and "roles" are present, the converter
         * produces authorities from both claims combined.
         *
         * <p>A token from the Authorization Server will typically have:
         * <pre>
         *   "scope": "products.read",
         *   "roles": ["ROLE_READER"]
         * </pre>
         * The resource server should see both SCOPE_products.read and ROLE_READER
         * as authorities, enabling both scope checks and role checks.
         */
        @Test
        @DisplayName("scope + roles produces combined authorities")
        void scopeAndRolesProduceCombinedAuthorities() {
            Jwt jwt = buildJwt(Map.of(
                    "scope", "products.read products.write",
                    "roles", List.of("ROLE_READER", "ROLE_WRITER")
            ));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder(
                            "SCOPE_products.read",
                            "SCOPE_products.write",
                            "ROLE_READER",
                            "ROLE_WRITER"
                    );
        }

        /**
         * Verifies that a JWT with no scope and no roles produces an empty collection.
         * This represents an anonymous-like token with no authorization.
         */
        @Test
        @DisplayName("JWT with no scope and no roles produces empty authorities")
        void noScopeNoRolesProducesEmptyAuthorities() {
            // JWT with only standard claims (sub, iss) — no scope or roles
            Jwt jwt = buildJwt(Map.of(
                    "sub", "test-client",
                    "iss", "http://localhost:9000"
            ));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).isEmpty();
        }

        /**
         * Verifies that whitespace-separated scopes are split correctly,
         * producing exactly one authority per scope value.
         */
        @Test
        @DisplayName("multiple scopes with extra whitespace are handled correctly")
        void multipleSpaceSeparatedScopesAreHandled() {
            // Scope string with extra whitespace between tokens
            Jwt jwt = buildJwt(Map.of("scope", "products.read  products.write"));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).hasSize(2);
            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("SCOPE_products.read", "SCOPE_products.write");
        }
    }
}
