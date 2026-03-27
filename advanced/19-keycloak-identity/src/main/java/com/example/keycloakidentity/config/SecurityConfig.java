package com.example.keycloakidentity.config;

import com.example.keycloakidentity.security.KeycloakJwtAuthoritiesConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the Keycloak Identity application.
 *
 * <p>This class configures the application to act as an OAuth2 Resource Server that
 * trusts tokens issued by Keycloak. The key responsibilities are:
 * <ol>
 *   <li>Enable stateless JWT-based Bearer token authentication.</li>
 *   <li>Define which endpoints are public and which require a valid Keycloak token.</li>
 *   <li>Configure the JWT-to-authorities converter that understands Keycloak's
 *       specific claims structure ({@code realm_access.roles}).</li>
 *   <li>Enforce role-based access control at the HTTP filter layer.</li>
 * </ol>
 *
 * <h2>Keycloak JWT structure</h2>
 * A Keycloak-issued JWT contains roles in a nested structure:
 * <pre>
 * {
 *   "sub": "user-uuid",
 *   "preferred_username": "john.doe",
 *   "email": "john@example.com",
 *   "realm_access": {
 *     "roles": ["USER", "ADMIN", "offline_access", "uma_authorization"]
 *   },
 *   "resource_access": {
 *     "my-client": {
 *       "roles": ["client-role"]
 *     }
 *   },
 *   "scope": "openid email profile"
 * }
 * </pre>
 * Spring Security's default converter does NOT read {@code realm_access.roles}.
 * Our custom {@link KeycloakJwtAuthoritiesConverter} handles this correctly.
 *
 * <h2>Authorization model</h2>
 * <ul>
 *   <li>{@code ROLE_USER}  — can read user data (GET endpoints)</li>
 *   <li>{@code ROLE_ADMIN} — can read and write user data (all endpoints)</li>
 * </ul>
 *
 * <h2>Why STATELESS?</h2>
 * A backend API secured with JWT Bearer tokens must never create HTTP sessions.
 * Every request is self-contained: it carries the JWT which proves the caller's
 * identity and permissions. Sessions would add unnecessary state and memory overhead.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize / @PostAuthorize on controller methods
public class SecurityConfig {

    /**
     * Defines the security filter chain that applies to all HTTP requests.
     *
     * <p>Authorization rules (evaluated top-to-bottom, first match wins):
     * <ul>
     *   <li>Public: {@code GET /api/public/**}    — no token required</li>
     *   <li>Public: {@code GET /actuator/health}  — Docker probe, no token required</li>
     *   <li>Public: {@code GET /actuator/info}    — general info, no token required</li>
     *   <li>{@code GET /api/users/me}             — any authenticated user (own profile)</li>
     *   <li>{@code GET /api/users/**}             — requires {@code ROLE_USER} or {@code ROLE_ADMIN}</li>
     *   <li>{@code POST/PUT/DELETE /api/users/**} — requires {@code ROLE_ADMIN}</li>
     *   <li>All other requests                   — require authentication (valid JWT)</li>
     * </ul>
     *
     * @param http the HttpSecurity builder
     * @return the configured SecurityFilterChain
     * @throws Exception if the configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // -------------------------------------------------------------------
                // STATELESS session management.
                // No HTTP sessions are created — every request must carry its own JWT.
                // -------------------------------------------------------------------
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // -------------------------------------------------------------------
                // Disable CSRF protection.
                // CSRF only applies to browser-based session authentication (cookies).
                // JWT Bearer tokens are not sent automatically by browsers, so REST
                // APIs using JWTs are not vulnerable to CSRF attacks.
                // -------------------------------------------------------------------
                .csrf(csrf -> csrf.disable())

                // -------------------------------------------------------------------
                // Authorization rules
                // -------------------------------------------------------------------
                .authorizeHttpRequests(authorize -> authorize

                        // ---- Public endpoints — no authentication required ----
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // ---- /api/users/me — any valid token can access own profile ----
                        // This must come BEFORE the broader /api/users/** rule.
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()

                        // ---- Read endpoints — USER or ADMIN role ----
                        // Spring Security prefixes Keycloak roles with "ROLE_" via our converter,
                        // so hasRole("USER") checks for the authority "ROLE_USER".
                        .requestMatchers(HttpMethod.GET, "/api/users/**")
                                .hasAnyRole("USER", "ADMIN")

                        // ---- Write endpoints — ADMIN role only ----
                        .requestMatchers(HttpMethod.POST, "/api/users/**")
                                .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/**")
                                .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/**")
                                .hasRole("ADMIN")

                        // ---- All other requests — require a valid token ----
                        .anyRequest().authenticated()
                )

                // -------------------------------------------------------------------
                // OAuth2 Resource Server — JWT validation via Keycloak JWKS.
                //
                // Spring Security will:
                //   1. Extract the Bearer token from the Authorization header.
                //   2. Fetch Keycloak's public keys from the JWKS URI
                //      (spring.security.oauth2.resourceserver.jwt.jwk-set-uri).
                //   3. Verify the JWT signature and validate standard claims (exp, iss).
                //   4. Convert the validated JWT to a JwtAuthenticationToken using our
                //      custom converter that understands Keycloak's roles structure.
                // -------------------------------------------------------------------
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * Configures the JWT authentication converter.
     *
     * <p>The default Spring Security {@code JwtGrantedAuthoritiesConverter} only reads
     * the {@code scope} or {@code scp} claim. Keycloak stores roles in
     * {@code realm_access.roles}, which the default converter completely ignores.
     *
     * <p>Our custom {@link KeycloakJwtAuthoritiesConverter} reads both the standard
     * {@code scope} claim and Keycloak's {@code realm_access.roles} claim, ensuring
     * that role-based access control works correctly with Keycloak-issued tokens.
     *
     * @return the JwtAuthenticationConverter configured with our Keycloak-aware converter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        // Replace the default converter with our Keycloak-aware implementation.
        // This is the critical customization that makes Keycloak role-based auth work.
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakJwtAuthoritiesConverter());

        return converter;
    }
}
