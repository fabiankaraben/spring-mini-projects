package com.example.resourceserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the OAuth2 Resource Server application.
 *
 * <p>This application demonstrates how to build a Spring Boot REST API that acts
 * as an OAuth2 Resource Server. A resource server is a backend service that
 * exposes protected resources (here: a Products API) and validates the OAuth2
 * access tokens presented by clients before granting or denying access.
 *
 * <p><b>How OAuth2 Resource Server validation works:</b>
 * <ol>
 *   <li>A client (e.g., a mobile app or another microservice) first obtains a
 *       JWT access token from an OAuth2 Authorization Server.</li>
 *   <li>The client includes the token in the {@code Authorization: Bearer <token>}
 *       header when calling this resource server.</li>
 *   <li>Spring Security's {@code BearerTokenAuthenticationFilter} extracts the
 *       Bearer token from the request.</li>
 *   <li>The {@code JwtDecoder} validates the token:
 *       <ul>
 *         <li>Fetches the Authorization Server's public keys from the JWK Set URI
 *             ({@code /oauth2/jwks}) — cached after first fetch.</li>
 *         <li>Verifies the JWT signature using the fetched RSA public key.</li>
 *         <li>Validates the {@code exp}, {@code iss}, and {@code aud} claims.</li>
 *       </ul>
 *   </li>
 *   <li>A {@code JwtAuthenticationConverter} maps the JWT claims (scopes, roles) to
 *       Spring Security {@code GrantedAuthority} objects for authorization checks.</li>
 *   <li>The HTTP request proceeds to the controller only if the token is valid
 *       and the required scopes/authorities are present.</li>
 * </ol>
 *
 * <p><b>Protected Products API endpoints:</b>
 * <ul>
 *   <li>{@code GET  /api/products}         — list all products (scope: products.read)</li>
 *   <li>{@code GET  /api/products/{id}}    — get product by ID (scope: products.read)</li>
 *   <li>{@code POST /api/products}         — create a product (scope: products.write)</li>
 *   <li>{@code PUT  /api/products/{id}}    — update a product (scope: products.write)</li>
 *   <li>{@code DELETE /api/products/{id}} — delete a product (scope: products.write)</li>
 * </ul>
 *
 * <p><b>Public endpoints (no token required):</b>
 * <ul>
 *   <li>{@code GET /actuator/health} — health check for Docker/Kubernetes probes</li>
 *   <li>{@code GET /api/public/info} — server information (demo of public endpoint)</li>
 * </ul>
 */
@SpringBootApplication
public class ResourceServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourceServerApplication.class, args);
    }
}
