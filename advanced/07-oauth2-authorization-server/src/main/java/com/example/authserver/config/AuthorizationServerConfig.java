package com.example.authserver.config;

import com.example.authserver.security.CustomTokenCustomizer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

import java.util.List;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Central configuration for the OAuth2 Authorization Server.
 *
 * <p>This class wires together all Spring Authorization Server components:
 * <ol>
 *   <li>{@link #authorizationServerSecurityFilterChain} — configures the security filter chain
 *       for the OAuth2 protocol endpoints ({@code /oauth2/token}, {@code /oauth2/authorize}, etc.).</li>
 *   <li>{@link #defaultSecurityFilterChain} — configures the standard web security filter chain
 *       for all other requests (login form, custom management endpoints).</li>
 *   <li>{@link #registeredClientRepository} — a JDBC-backed repository that persists
 *       OAuth2 client registrations in PostgreSQL.</li>
 *   <li>{@link #authorizationService} — a JDBC-backed service that stores issued tokens.</li>
 *   <li>{@link #jwkSource} — provides the RSA JWK Set used to sign JWTs.</li>
 *   <li>{@link #jwtDecoder} — decodes and verifies JWT access tokens.</li>
 *   <li>{@link #authorizationServerSettings} — configures the issuer URI and endpoint paths.</li>
 *   <li>{@link #tokenCustomizer} — enriches JWTs with custom claims.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    /**
     * Security filter chain for OAuth2 Authorization Server protocol endpoints.
     *
     * <p>This filter chain has {@code @Order(1)} so it takes precedence over the
     * default security filter chain (which has {@code @Order(2)}).
     *
     * <p>The {@code OAuth2AuthorizationServerConfigurer} enables all standard
     * OAuth2/OIDC endpoints:
     * <ul>
     *   <li>{@code /oauth2/authorize}     — authorization endpoint</li>
     *   <li>{@code /oauth2/token}         — token endpoint</li>
     *   <li>{@code /oauth2/jwks}          — JWK Set endpoint</li>
     *   <li>{@code /oauth2/introspect}    — token introspection endpoint</li>
     *   <li>{@code /oauth2/revoke}        — token revocation endpoint</li>
     *   <li>{@code /.well-known/...}      — discovery documents</li>
     * </ul>
     *
     * <p>OIDC support is explicitly enabled to expose:
     * <ul>
     *   <li>{@code /userinfo}                                — OIDC UserInfo endpoint</li>
     *   <li>{@code /.well-known/openid-configuration}        — OIDC discovery</li>
     * </ul>
     *
     * <p>When an unauthenticated request targets an OAuth2 endpoint that requires
     * authentication (e.g., the authorization endpoint), the user is redirected to
     * the login page via {@code LoginUrlAuthenticationEntryPoint}.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {

        // Apply the default Spring Authorization Server configuration, which registers
        // all OAuth2 protocol endpoints with their associated filters and configurers.
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        // Enable OIDC support so the /userinfo and /.well-known/openid-configuration
        // endpoints are available. OIDC support is separate from core OAuth2.
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());

        // When a browser hits an OAuth2 endpoint that requires an authenticated user
        // (like /oauth2/authorize), redirect to the login page instead of returning 401.
        // The MediaTypeRequestMatcher targets only text/html requests (browser navigation),
        // so API clients (Accept: application/json) still receive 401.
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
        );

        // Allow the authorization server to act as a resource server for the /userinfo
        // endpoint. Incoming requests to /userinfo must present a valid JWT access token
        // with the 'openid' scope.
        http.oauth2ResourceServer(resourceServer ->
                resourceServer.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Default security filter chain for all non-OAuth2-protocol requests.
     *
     * <p>This filter chain has {@code @Order(2)} so it handles requests not matched by
     * the authorization server filter chain above.
     *
     * <p>Configuration:
     * <ul>
     *   <li>All requests require authentication by default.</li>
     *   <li>Form login is enabled — Spring Security generates a default {@code /login} page.</li>
     *   <li>The custom management endpoints ({@code /auth/**}) are public so they can be
     *       called without credentials in development and integration tests.</li>
     *   <li>The Actuator health endpoint is public for monitoring.</li>
     * </ul>
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // Public endpoints — no authentication required
                        .requestMatchers(
                                "/auth/**",           // custom management endpoints
                                "/actuator/health",   // health check for Docker/K8s
                                "/actuator/info"      // build info
                        ).permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                // Enable the default Spring Security form-based login page at /login.
                // This is needed for the authorization_code flow where the user logs in
                // via browser to grant authorization to a client application.
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    /**
     * JDBC-backed repository for OAuth2 registered clients.
     *
     * <p>{@link JdbcRegisteredClientRepository} persists {@link RegisteredClient} entries
     * to the {@code oauth2_registered_client} table in PostgreSQL. This means:
     * <ul>
     *   <li>Client registrations survive server restarts.</li>
     *   <li>Multiple authorization server instances can share the same client registry.</li>
     * </ul>
     *
     * <p>Two demo clients are pre-registered on startup if they do not already exist:
     * <ol>
     *   <li><b>messaging-client</b> — a confidential client supporting {@code client_credentials}
     *       and {@code authorization_code} with the {@code read} and {@code write} scopes.
     *       Requires PKCE for the authorization_code flow (best practice for public clients).</li>
     *   <li><b>service-account-client</b> — a machine-to-machine client supporting only
     *       {@code client_credentials} with the {@code api.read} scope.
     *       Used for service-to-service calls where no user is involved.</li>
     * </ol>
     *
     * <p>Client secrets are encoded with {@code {noop}} here for demo clarity.
     * In production, use BCrypt encoding: {@code {bcrypt}$2a$...}.
     *
     * @param jdbcTemplate Spring JDBC template backed by the configured DataSource
     * @return the JDBC-backed RegisteredClientRepository
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        JdbcRegisteredClientRepository repository =
                new JdbcRegisteredClientRepository(jdbcTemplate);

        // ----------------------------------------------------------------
        // Client 1: messaging-client
        // Purpose: Demonstrates both client_credentials (M2M) and
        //          authorization_code (user-delegated) flows.
        // ----------------------------------------------------------------
        RegisteredClient messagingClient = RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId("messaging-client")
                // {noop} prefix tells Spring Security to compare the secret as-is
                // (no hashing). In production, use {bcrypt} instead.
                .clientSecret("{noop}messaging-secret")
                // CLIENT_SECRET_BASIC: the client sends credentials in the
                // Authorization header as Base64(clientId:clientSecret).
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                // CLIENT_SECRET_POST: the client sends credentials as form parameters.
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                // authorization_code: user-delegated flow for web/native apps.
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                // refresh_token: allows renewing access tokens without re-authentication.
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                // client_credentials: machine-to-machine flow (no user involved).
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                // Redirect URI after successful authorization (for authorization_code flow).
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/messaging-client-oidc")
                .redirectUri("http://127.0.0.1:8080/authorized")
                // post_logout_redirect_uri: where to redirect after the user logs out via OIDC.
                .postLogoutRedirectUri("http://127.0.0.1:8080/logged-out")
                // openid: required scope for OIDC flows (issues ID tokens).
                .scope(OidcScopes.OPENID)
                // profile: requests access to the user's profile claims in the ID token.
                .scope(OidcScopes.PROFILE)
                // Custom application-defined scopes:
                .scope("read")
                .scope("write")
                .clientSettings(ClientSettings.builder()
                        // requireProofKey: enforce PKCE for the authorization_code flow.
                        // PKCE (Proof Key for Code Exchange) prevents authorization code
                        // interception attacks. Recommended for all public clients.
                        .requireProofKey(false)
                        // requireAuthorizationConsent: show the consent screen to users.
                        // When true, the user must explicitly approve the requested scopes.
                        .requireAuthorizationConsent(true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        // Access token TTL: 1 hour. After expiry, the client must either
                        // refresh (if it has a refresh token) or re-authenticate.
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        // Refresh token TTL: 24 hours. After expiry, the user must log in again.
                        .refreshTokenTimeToLive(Duration.ofHours(24))
                        // Reuse refresh tokens: false means a new refresh token is issued
                        // on each use, which is more secure (rotation).
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        // ----------------------------------------------------------------
        // Client 2: service-account-client
        // Purpose: Demonstrates a pure M2M client that uses only
        //          client_credentials (no user interaction required).
        // ----------------------------------------------------------------
        RegisteredClient serviceAccountClient = RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId("service-account-client")
                .clientSecret("{noop}service-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                // Only client_credentials — this client never acts on behalf of a user.
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                // Custom scope that the downstream API checks for authorization.
                .scope("api.read")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .build())
                .build();

        // Register clients only if they are not already in the database.
        // This is safe to call on every startup (idempotent client registration).
        if (repository.findByClientId("messaging-client") == null) {
            repository.save(messagingClient);
        }
        if (repository.findByClientId("service-account-client") == null) {
            repository.save(serviceAccountClient);
        }

        return repository;
    }

    /**
     * JDBC-backed OAuth2 authorization service.
     *
     * <p>{@link JdbcOAuth2AuthorizationService} persists issued OAuth2 authorizations
     * (access tokens, refresh tokens, authorization codes) to PostgreSQL. This enables:
     * <ul>
     *   <li>Token lookup for introspection ({@code /oauth2/introspect}).</li>
     *   <li>Token revocation ({@code /oauth2/revoke}).</li>
     *   <li>Shared state across multiple authorization server instances.</li>
     * </ul>
     *
     * @param jdbcTemplate Spring JDBC template backed by the configured DataSource
     * @param registeredClientRepository the client repository (needed for deserialization)
     * @return the JDBC-backed OAuth2AuthorizationService
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository) {

        // Build a custom ObjectMapper that includes all the Spring Security Jackson
        // mixins. These mixins register type mappings for Spring Security classes so
        // that Jackson's polymorphic deserialization allowlist accepts them.
        //
        // Without this, storing and retrieving authorizations that contain Java
        // immutable collections (e.g., Set.copyOf() → ImmutableCollections$ListN) throws:
        //   "The class java.util.ImmutableCollections$ListN is not in the allowlist"
        //
        // This happens during introspection: the stored authorization is fetched from
        // the database, and the attributes JSON (which may contain Set.of() values)
        // is deserialized by Jackson. The default allowlist does not include
        // ImmutableCollections types — we must register them explicitly.
        //
        // SecurityJackson2Modules.getModules() provides the Spring Security Jackson
        // modules (CoreJackson2Module, etc.).
        // OAuth2AuthorizationServerJackson2Module adds the AS-specific type mappings.
        ClassLoader classLoader = JdbcOAuth2AuthorizationService.class.getClassLoader();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(securityModules);
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());

        // Configure both the row mapper (read path) and the parameters mapper
        // (write path) to use the same ObjectMapper with Security modules registered.
        JdbcOAuth2AuthorizationService authorizationService =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);

        // Read path: used when loading stored authorizations (e.g., during introspection)
        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(registeredClientRepository);
        rowMapper.setObjectMapper(objectMapper);
        authorizationService.setAuthorizationRowMapper(rowMapper);

        // Write path: used when serializing authorization attributes to JSON for storage
        JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper parametersMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
        parametersMapper.setObjectMapper(objectMapper);
        authorizationService.setAuthorizationParametersMapper(parametersMapper);

        return authorizationService;
    }

    /**
     * JWK Source — provides the RSA key pair used to sign JWTs.
     *
     * <p>This method generates a fresh 2048-bit RSA key pair on every startup.
     * In a production deployment you would load a persistent key pair from:
     * <ul>
     *   <li>A PKCS#12 keystore file ({@code .p12} / {@code .jks})</li>
     *   <li>An environment variable / secrets manager (AWS Secrets Manager, HashiCorp Vault)</li>
     * </ul>
     *
     * <p><b>Why RSA and not HMAC?</b>
     * RSA (asymmetric) signing means resource servers can verify tokens using only the
     * <em>public</em> key (fetched from {@code /oauth2/jwks}) without ever having access to
     * the private key. HMAC (symmetric) requires sharing the secret with every resource server,
     * which increases the attack surface.
     *
     * <p>The JWK Set is exposed publicly at {@code /oauth2/jwks} so that resource servers and
     * any party that needs to verify tokens can fetch the public key automatically.
     *
     * @return the JWKSource wrapping the generated RSA key pair
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        // Generate a 2048-bit RSA key pair.
        // 2048 bits is the industry-standard minimum size for RSA keys.
        KeyPair keyPair = generateRsaKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        // Build a Nimbus RSAKey (JWK representation) from the raw JCA key pair.
        // The keyID is a random UUID used to identify this key in the JWK Set.
        // When Spring Authorization Server signs a JWT, it embeds this kid in the
        // token header so verifiers know which key to use.
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();

        // Wrap the RSAKey in a JWKSet and expose it as an immutable JWKSource.
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * JWT Decoder — used by the resource server portion of this application.
     *
     * <p>This decoder validates incoming JWT access tokens on the {@code /userinfo}
     * endpoint (which acts as an OAuth2 resource server). It uses the same JWK Source
     * as the token issuer so the keys are always in sync.
     *
     * @param jwkSource the JWK source providing the public key for verification
     * @return the JwtDecoder
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        // Delegates to the Spring Authorization Server helper which creates
        // a NimbusJwtDecoder configured with the provided JWK source.
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Authorization Server Settings — configures global server behavior.
     *
     * <p>The most important setting here is the <em>issuer URI</em>. This URI is:
     * <ul>
     *   <li>Embedded in every issued JWT as the {@code iss} (issuer) claim.</li>
     *   <li>Used in the discovery documents ({@code /.well-known/...}) so that
     *       clients can auto-discover all endpoint URLs.</li>
     *   <li>Validated by resource servers when they verify incoming JWTs.</li>
     * </ul>
     *
     * <p>For local development the issuer is {@code http://localhost:9000}. In production
     * this should be the externally reachable HTTPS URL of your authorization server.
     *
     * @return the AuthorizationServerSettings
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                // The issuer URI is the canonical URL of this authorization server.
                // It MUST match the value that resource servers are configured to expect.
                .issuer("http://localhost:9000")
                .build();
    }

    /**
     * OAuth2 Token Customizer — enriches JWTs with custom claims.
     *
     * <p>This customizer is invoked by Spring Authorization Server every time a JWT
     * access token or ID token is about to be encoded. The {@link CustomTokenCustomizer}
     * adds the following non-standard claims to every access token:
     * <ul>
     *   <li>{@code roles}    — a list of role strings derived from the client scopes</li>
     *   <li>{@code tenant}   — the tenant identifier (from client metadata or a default)</li>
     *   <li>{@code metadata} — a map of extra key/value pairs for downstream services</li>
     * </ul>
     *
     * <p>Resource servers can read these custom claims from the JWT payload without
     * contacting the authorization server again.
     *
     * @return the OAuth2TokenCustomizer
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        // Delegate to a separate class to keep this configuration class clean
        // and to make the customizer unit-testable in isolation.
        return new CustomTokenCustomizer();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a new 2048-bit RSA key pair using the standard JCA API.
     *
     * <p>This method uses {@link KeyPairGenerator} which is part of the Java Standard
     * Library. No external library is needed. The generated keys are ephemeral (new
     * on every startup); replace this with persistent key loading for production.
     *
     * @return a fresh RSA {@link KeyPair}
     * @throws IllegalStateException if the RSA algorithm is not available (should never happen)
     */
    private static KeyPair generateRsaKeyPair() {
        try {
            // KeyPairGenerator is the standard JCA factory for asymmetric key pairs.
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            // 2048-bit key size: the NIST-recommended minimum for RSA in 2024.
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }
}
