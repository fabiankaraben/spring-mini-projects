package com.example.resourceserver.config;

import com.example.resourceserver.security.JwtClaimsAuthoritiesConverter;
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
 * Security configuration for the OAuth2 Resource Server.
 *
 * <p>This class configures Spring Security to act as an OAuth2 Resource Server.
 * The key responsibilities are:
 * <ol>
 *   <li>Enable JWT-based Bearer token validation for protected endpoints.</li>
 *   <li>Define which endpoints are public and which require a valid token.</li>
 *   <li>Configure how JWT claims are converted into Spring Security authorities.</li>
 *   <li>Enforce scope-based authorization at the HTTP layer.</li>
 * </ol>
 *
 * <p><b>JWT validation flow:</b>
 * When a request with an {@code Authorization: Bearer <token>} header arrives:
 * <ol>
 *   <li>{@code BearerTokenAuthenticationFilter} extracts the token.</li>
 *   <li>{@code NimbusJwtDecoder} fetches the JWK Set from the authorization server
 *       (configured via {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri})
 *       and verifies the JWT signature.</li>
 *   <li>{@code JwtAuthenticationConverter} converts the validated JWT into a
 *       {@code JwtAuthenticationToken} with the appropriate authorities.</li>
 *   <li>The security filter chain checks whether the request's required authorities
 *       match the token's authorities.</li>
 * </ol>
 *
 * <p><b>Session management:</b>
 * REST APIs should be stateless — no HTTP session is created or used.
 * Each request must carry its own Bearer token.
 *
 * <p><b>CSRF:</b>
 * CSRF protection is disabled because:
 * <ul>
 *   <li>REST APIs using Bearer tokens are not vulnerable to CSRF attacks.</li>
 *   <li>CSRF is only relevant when the browser automatically sends credentials
 *       (cookies/session). Bearer tokens are not automatically sent.</li>
 * </ul>
 *
 * <p><b>Method security:</b>
 * {@code @EnableMethodSecurity} enables {@code @PreAuthorize} annotations on
 * controller methods, allowing fine-grained authorization checks per method.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize / @PostAuthorize on controller methods
public class ResourceServerSecurityConfig {

    /**
     * Configures the security filter chain for the resource server.
     *
     * <p>Authorization rules:
     * <ul>
     *   <li>Public: {@code GET /api/public/**} — no token required.</li>
     *   <li>Public: {@code GET /actuator/health} — Docker/K8s probe, no token required.</li>
     *   <li>{@code GET /api/products/**} — requires scope {@code products.read}.</li>
     *   <li>{@code POST /api/products} — requires scope {@code products.write}.</li>
     *   <li>{@code PUT /api/products/**} — requires scope {@code products.write}.</li>
     *   <li>{@code DELETE /api/products/**} — requires scope {@code products.write}.</li>
     *   <li>All other requests — require authentication (valid token).</li>
     * </ul>
     *
     * <p>Spring Security maps OAuth2 scopes to authorities with the prefix {@code SCOPE_}.
     * So a JWT with {@code "scope": "products.read"} produces the authority
     * {@code SCOPE_products.read}. The {@code hasAuthority("SCOPE_products.read")} check
     * matches this authority.
     *
     * <p>Alternatively, our {@link JwtClaimsAuthoritiesConverter} also maps the custom
     * {@code roles} claim (e.g., {@code ROLE_READER}) directly, enabling role-based
     * access control via {@code hasRole("READER")} in {@code @PreAuthorize} annotations.
     *
     * @param http the HttpSecurity builder
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ---------------------------------------------------------------
                // Session management: STATELESS
                // A resource server must never create HTTP sessions. Every request
                // is independently authenticated via its Bearer token.
                // ---------------------------------------------------------------
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ---------------------------------------------------------------
                // CSRF: disabled for REST APIs.
                // Bearer token authentication is not susceptible to CSRF attacks
                // because tokens are not automatically sent by browsers.
                // ---------------------------------------------------------------
                .csrf(csrf -> csrf.disable())

                // ---------------------------------------------------------------
                // Authorization rules (evaluated top-to-bottom, first match wins)
                // ---------------------------------------------------------------
                .authorizeHttpRequests(authorize -> authorize

                        // Public endpoints — no authentication required
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Products read endpoints require SCOPE_products.read authority.
                        // Spring Security prefixes OAuth2 scopes with "SCOPE_" when
                        // converting them to GrantedAuthority objects.
                        .requestMatchers(HttpMethod.GET, "/api/products/**")
                                .hasAuthority("SCOPE_products.read")

                        // Products write endpoints require SCOPE_products.write authority.
                        .requestMatchers(HttpMethod.POST, "/api/products")
                                .hasAuthority("SCOPE_products.write")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**")
                                .hasAuthority("SCOPE_products.write")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**")
                                .hasAuthority("SCOPE_products.write")

                        // All other requests require a valid token (any scope)
                        .anyRequest().authenticated()
                )

                // ---------------------------------------------------------------
                // OAuth2 Resource Server: JWT validation
                //
                // Tells Spring Security to:
                //   1. Accept Bearer tokens in the Authorization header.
                //   2. Validate JWTs using the JwkSet URI configured in application.yml.
                //   3. Use our custom JwtAuthenticationConverter to convert JWT claims
                //      into Spring Security authorities.
                // ---------------------------------------------------------------
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
     * <p>The {@link JwtAuthenticationConverter} is responsible for transforming a
     * validated {@link org.springframework.security.oauth2.jwt.Jwt} object into a
     * {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}
     * that carries a set of {@link org.springframework.security.core.GrantedAuthority} objects.
     *
     * <p>We use a custom {@link JwtClaimsAuthoritiesConverter} that extracts authorities from:
     * <ol>
     *   <li>The standard {@code scope} or {@code scp} claim — produces {@code SCOPE_*} authorities.</li>
     *   <li>The custom {@code roles} claim (added by the Authorization Server's token customizer)
     *       — produces {@code ROLE_*} authorities directly.</li>
     * </ol>
     *
     * <p>This means a token with:
     * <pre>
     *   { "scope": "products.read", "roles": ["ROLE_READER"] }
     * </pre>
     * will produce the authorities: {@code [SCOPE_products.read, ROLE_READER]}.
     *
     * @return the configured JwtAuthenticationConverter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        // Replace the default GrantedAuthoritiesConverter with our custom one that
        // also reads the "roles" claim from the JWT.
        converter.setJwtGrantedAuthoritiesConverter(new JwtClaimsAuthoritiesConverter());

        return converter;
    }
}
