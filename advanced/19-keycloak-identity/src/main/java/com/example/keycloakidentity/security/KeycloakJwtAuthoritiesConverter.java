package com.example.keycloakidentity.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Custom JWT authorities converter that understands Keycloak's specific claims structure.
 *
 * <h2>Why a custom converter is needed</h2>
 * Spring Security's default {@code JwtGrantedAuthoritiesConverter} only reads the standard
 * OAuth2 {@code scope} or {@code scp} claim and produces {@code SCOPE_*} authorities.
 * Keycloak stores user roles in a <em>nested</em> structure that the default converter
 * completely ignores:
 *
 * <pre>
 * {
 *   "realm_access": {
 *     "roles": ["USER", "ADMIN", "offline_access"]
 *   },
 *   "resource_access": {
 *     "my-client-id": {
 *       "roles": ["client-specific-role"]
 *     }
 *   },
 *   "scope": "openid email profile"
 * }
 * </pre>
 *
 * <h2>What this converter produces</h2>
 * Given the JWT above (with client ID = "my-client-id"), this converter produces:
 * <pre>
 *   [ROLE_USER, ROLE_ADMIN, ROLE_offline_access, SCOPE_openid, SCOPE_email, SCOPE_profile, ROLE_client-specific-role]
 * </pre>
 *
 * <ul>
 *   <li>Realm roles get the {@code ROLE_} prefix — enabling {@code hasRole("USER")} checks.</li>
 *   <li>Scope values get the {@code SCOPE_} prefix — enabling {@code hasAuthority("SCOPE_openid")} checks.</li>
 *   <li>Client-specific roles also get the {@code ROLE_} prefix.</li>
 * </ul>
 *
 * <h2>Spring Security role naming convention</h2>
 * Spring Security's {@code hasRole("USER")} helper automatically prepends {@code ROLE_},
 * so it checks for the authority {@code ROLE_USER}. Our converter follows this convention
 * by adding the {@code ROLE_} prefix to all Keycloak realm roles.
 *
 * <p>This class is configured as the authorities converter in {@link com.example.keycloakidentity.config.SecurityConfig}.
 */
public class KeycloakJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /**
     * Keycloak JWT claim that contains realm-level roles.
     * Structure: {@code {"realm_access": {"roles": ["USER", "ADMIN"]}}}
     */
    private static final String REALM_ACCESS_CLAIM = "realm_access";

    /**
     * The key inside the realm_access map that holds the list of role strings.
     */
    private static final String ROLES_KEY = "roles";

    /**
     * Keycloak JWT claim that contains client-level (resource) roles.
     * Structure: {@code {"resource_access": {"client-id": {"roles": ["role1"]}}}}
     */
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";

    /**
     * Standard OAuth2 scope claim — space-separated string of granted scopes.
     */
    private static final String SCOPE_CLAIM = "scope";

    /**
     * Authority prefix for roles, following Spring Security's naming convention.
     * {@code hasRole("USER")} checks for the authority {@code ROLE_USER}.
     */
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Authority prefix for OAuth2 scopes.
     * {@code hasAuthority("SCOPE_openid")} checks for this prefix.
     */
    private static final String SCOPE_PREFIX = "SCOPE_";

    /**
     * The Keycloak client ID whose resource_access roles should also be included.
     * When null, only realm_access roles and scopes are extracted.
     * Set via constructor for testability.
     */
    private final String resourceId;

    /**
     * Creates a converter that reads only realm-level roles and scopes.
     * Client-specific roles ({@code resource_access}) are not extracted.
     */
    public KeycloakJwtAuthoritiesConverter() {
        this.resourceId = null;
    }

    /**
     * Creates a converter that reads realm-level roles, scopes, AND client-specific
     * roles for the given Keycloak client ID.
     *
     * @param resourceId the Keycloak client ID whose resource_access roles to include
     */
    public KeycloakJwtAuthoritiesConverter(String resourceId) {
        this.resourceId = resourceId;
    }

    /**
     * Converts the Keycloak JWT claims into a collection of Spring Security authorities.
     *
     * <p>Processing order:
     * <ol>
     *   <li>Extract realm roles from {@code realm_access.roles} → {@code ROLE_*} authorities.</li>
     *   <li>If {@code resourceId} is configured, extract client roles from
     *       {@code resource_access.{resourceId}.roles} → {@code ROLE_*} authorities.</li>
     *   <li>Extract scopes from the {@code scope} claim → {@code SCOPE_*} authorities.</li>
     * </ol>
     *
     * @param jwt the validated Keycloak JWT
     * @return a collection of granted authorities; never null
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // -----------------------------------------------------------------------
        // Step 1: Extract realm-level roles from realm_access.roles
        //
        // Keycloak stores realm roles in a nested map:
        //   "realm_access": { "roles": ["USER", "ADMIN", "offline_access"] }
        //
        // We map each role to ROLE_<role> to follow Spring Security's convention.
        // -----------------------------------------------------------------------
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess != null && realmAccess.containsKey(ROLES_KEY)) {
            Object rolesValue = realmAccess.get(ROLES_KEY);
            if (rolesValue instanceof List<?> roleList) {
                // Java 21 pattern matching for instanceof — safer than explicit cast
                for (Object role : roleList) {
                    if (role instanceof String roleString && !roleString.isBlank()) {
                        // Add ROLE_ prefix so Spring's hasRole("USER") works correctly
                        authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleString));
                    }
                }
            }
        }

        // -----------------------------------------------------------------------
        // Step 2: Extract client-level roles from resource_access.{resourceId}.roles
        //
        // Keycloak also supports per-client roles, stored in:
        //   "resource_access": {
        //     "my-client": { "roles": ["client-role-1"] }
        //   }
        //
        // This is optional — only processed when resourceId is configured.
        // -----------------------------------------------------------------------
        if (resourceId != null && !resourceId.isBlank()) {
            Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS_CLAIM);
            if (resourceAccess != null && resourceAccess.containsKey(resourceId)) {
                Object clientAccessObj = resourceAccess.get(resourceId);
                if (clientAccessObj instanceof Map<?, ?> clientAccess) {
                    Object clientRolesObj = clientAccess.get(ROLES_KEY);
                    if (clientRolesObj instanceof List<?> clientRoles) {
                        for (Object role : clientRoles) {
                            if (role instanceof String roleString && !roleString.isBlank()) {
                                authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleString));
                            }
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------------------------
        // Step 3: Extract OAuth2 scopes from the standard "scope" claim
        //
        // The scope claim is a space-separated string per RFC 6749:
        //   "scope": "openid email profile"
        //
        // We produce SCOPE_openid, SCOPE_email, SCOPE_profile authorities.
        // These are useful for hasAuthority("SCOPE_openid") checks.
        // -----------------------------------------------------------------------
        String scopeClaimValue = jwt.getClaimAsString(SCOPE_CLAIM);
        if (scopeClaimValue != null && !scopeClaimValue.isBlank()) {
            for (String scope : scopeClaimValue.split("\\s+")) {
                if (!scope.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + scope));
                }
            }
        }

        return authorities;
    }
}
