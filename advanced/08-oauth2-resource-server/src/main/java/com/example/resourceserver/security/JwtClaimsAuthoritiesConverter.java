package com.example.resourceserver.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Custom JWT authorities converter that extracts Spring Security
 * {@link GrantedAuthority} objects from two JWT claim sources:
 *
 * <ol>
 *   <li><b>Standard {@code scope} / {@code scp} claim</b> — the OAuth2 standard way
 *       to convey authorization scopes. Each scope value becomes a {@code SCOPE_*}
 *       authority (e.g., {@code "products.read"} → {@code SCOPE_products.read}).</li>
 *   <li><b>Custom {@code roles} claim</b> — a non-standard claim added by the
 *       Authorization Server's token customizer. Each value in the list is used
 *       directly as an authority (e.g., {@code "ROLE_READER"} → {@code ROLE_READER}).
 *       This enables {@code hasRole("READER")} and {@code hasAuthority("ROLE_READER")}
 *       checks in Spring Security.</li>
 * </ol>
 *
 * <p><b>Why a custom converter?</b>
 * Spring Security's default {@code JwtGrantedAuthoritiesConverter} only reads the
 * {@code scope} or {@code scp} claim and produces {@code SCOPE_*} authorities.
 * It does not read custom claims like {@code roles}. By replacing the default converter
 * with this one, we support both scope-based and role-based authorization without
 * any changes to the authorization rules configuration.
 *
 * <p><b>Example JWT payload:</b>
 * <pre>
 * {
 *   "sub": "service-account-client",
 *   "scope": "products.read products.write",
 *   "roles": ["ROLE_READER", "ROLE_WRITER"],
 *   "tenant": "internal",
 *   "exp": 1700000000
 * }
 * </pre>
 * Produces the following {@code GrantedAuthority} list:
 * <pre>
 *   [SCOPE_products.read, SCOPE_products.write, ROLE_READER, ROLE_WRITER]
 * </pre>
 *
 * <p>This class is used as a {@link Converter} argument to
 * {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter#setJwtGrantedAuthoritiesConverter}.
 */
public class JwtClaimsAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /**
     * The claim name for OAuth2 scopes (RFC 8693 standard claim).
     * Spring Authorization Server stores scopes in the "scope" claim as a
     * space-separated string.
     */
    private static final String SCOPE_CLAIM = "scope";

    /**
     * Alternative claim name for scopes used by some authorization servers.
     * Some servers (e.g., Microsoft Azure AD) use "scp" instead of "scope".
     */
    private static final String SCP_CLAIM = "scp";

    /**
     * The custom claim name added by the Authorization Server's token customizer.
     * Contains a JSON array of role strings like ["ROLE_READER", "ROLE_WRITER"].
     */
    private static final String ROLES_CLAIM = "roles";

    /**
     * The authority prefix for OAuth2 scope claims.
     * Spring Security convention: scope authorities have the "SCOPE_" prefix.
     */
    private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

    /**
     * Converts the JWT's claims into a collection of {@link GrantedAuthority} objects.
     *
     * <p>Processing order:
     * <ol>
     *   <li>Read the {@code scope} claim (space-separated string) and convert each
     *       space-delimited token into {@code SCOPE_<value>} authority.</li>
     *   <li>If {@code scope} is absent, try the {@code scp} claim.</li>
     *   <li>Read the {@code roles} claim (list of strings) and add each value
     *       as-is as a {@link SimpleGrantedAuthority} (they already have the
     *       {@code ROLE_} prefix from the authorization server).</li>
     * </ol>
     *
     * @param jwt the validated JWT from the authorization server
     * @return a collection of granted authorities; never null
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // -----------------------------------------------------------------------
        // Step 1: Extract scope-based authorities from the "scope" or "scp" claim.
        //
        // The "scope" claim is a space-separated string per RFC 6749.
        // Example: "scope": "products.read products.write"
        // → produces: [SCOPE_products.read, SCOPE_products.write]
        // -----------------------------------------------------------------------
        String scopeClaimValue = null;

        if (jwt.hasClaim(SCOPE_CLAIM)) {
            // "scope" claim is typically a space-separated string
            Object scopeValue = jwt.getClaim(SCOPE_CLAIM);
            scopeClaimValue = scopeValue != null ? scopeValue.toString() : null;
        } else if (jwt.hasClaim(SCP_CLAIM)) {
            // Fallback to "scp" for compatibility with other authorization servers
            Object scpValue = jwt.getClaim(SCP_CLAIM);
            scopeClaimValue = scpValue != null ? scpValue.toString() : null;
        }

        if (scopeClaimValue != null && !scopeClaimValue.isBlank()) {
            // Split on whitespace and create a SCOPE_* authority for each scope token
            for (String scope : scopeClaimValue.split("\\s+")) {
                if (!scope.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + scope));
                }
            }
        }

        // -----------------------------------------------------------------------
        // Step 2: Extract role-based authorities from the custom "roles" claim.
        //
        // The "roles" claim is a JSON array of strings added by the Authorization
        // Server's OAuth2TokenCustomizer. Each value already carries the "ROLE_"
        // prefix, so we use it directly without transformation.
        //
        // Example: "roles": ["ROLE_READER", "ROLE_WRITER"]
        // → produces: [ROLE_READER, ROLE_WRITER]
        // -----------------------------------------------------------------------
        if (jwt.hasClaim(ROLES_CLAIM)) {
            Object rolesValue = jwt.getClaim(ROLES_CLAIM);

            if (rolesValue instanceof List<?> roleList) {
                // Java 21+ pattern matching for instanceof — no explicit cast needed
                for (Object role : roleList) {
                    if (role instanceof String roleString && !roleString.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(roleString));
                    }
                }
            }
        }

        return authorities;
    }
}
