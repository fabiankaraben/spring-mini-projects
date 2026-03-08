package com.example.jwtgeneration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the JWT Generation mini-project.
 *
 * <p>This application demonstrates how a Spring Boot backend can issue
 * JSON Web Tokens (JWTs) upon successful username/password authentication.
 * The overall flow is:
 * <ol>
 *   <li>A client POSTs credentials to {@code /api/auth/login}.</li>
 *   <li>Spring Security validates the credentials against a PostgreSQL-backed
 *       {@code UserDetailsService}.</li>
 *   <li>If valid, the {@code JwtService} mints a signed JWT and returns it
 *       in the response body.</li>
 *   <li>The client can then attach the token in the
 *       {@code Authorization: Bearer <token>} header for subsequent requests.</li>
 * </ol>
 */
@SpringBootApplication
public class JwtGenerationApplication {

    public static void main(String[] args) {
        SpringApplication.run(JwtGenerationApplication.class, args);
    }
}
