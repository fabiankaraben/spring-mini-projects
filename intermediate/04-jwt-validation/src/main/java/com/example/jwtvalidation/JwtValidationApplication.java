package com.example.jwtvalidation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the JWT Validation Spring Boot application.
 *
 * <h2>What this project demonstrates</h2>
 * <p>While {@code 03-jwt-generation} showed how to <em>issue</em> a JWT upon
 * successful login, this project focuses on the next step: <strong>validating</strong>
 * incoming JWTs on every protected request.</p>
 *
 * <p>The key addition is a custom {@code JwtAuthenticationFilter} that sits in
 * the Spring Security filter chain and intercepts every HTTP request. For each
 * request the filter:</p>
 * <ol>
 *   <li>Reads the {@code Authorization: Bearer <token>} header.</li>
 *   <li>Extracts the username from the JWT's {@code sub} claim.</li>
 *   <li>Verifies the signature using HMAC-SHA256 and the configured secret.</li>
 *   <li>Checks that the token has not expired.</li>
 *   <li>Populates the Spring Security {@code SecurityContextHolder} so that
 *       downstream filters and controllers know who is making the request.</li>
 * </ol>
 *
 * <p>If any step fails the request is rejected with {@code 401 Unauthorized}
 * before it ever reaches a controller.</p>
 */
@SpringBootApplication
public class JwtValidationApplication {

    public static void main(String[] args) {
        SpringApplication.run(JwtValidationApplication.class, args);
    }
}
