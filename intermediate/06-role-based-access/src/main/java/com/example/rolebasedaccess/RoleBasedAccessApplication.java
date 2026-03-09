package com.example.rolebasedaccess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Role-Based Access mini-project.
 *
 * <p>This application demonstrates fine-grained, role-based access control
 * (RBAC) in a Spring Boot REST API using two complementary mechanisms:</p>
 *
 * <ol>
 *   <li><strong>URL-level security</strong> (in {@code SecurityConfig}) –
 *       coarse-grained rules applied to URL patterns before a request reaches
 *       any controller.</li>
 *   <li><strong>Method-level security via {@code @PreAuthorize}</strong>
 *       (on service and controller methods) – fine-grained rules evaluated
 *       against the caller's granted authorities right before the method executes.
 *       Enabled by {@code @EnableMethodSecurity} in {@code SecurityConfig}.</li>
 * </ol>
 *
 * <h2>Roles</h2>
 * <ul>
 *   <li>{@code ROLE_USER}  – standard authenticated user; read access to public resources.</li>
 *   <li>{@code ROLE_MODERATOR} – can manage content (edit, hide posts).</li>
 *   <li>{@code ROLE_ADMIN} – full access including user management and system operations.</li>
 * </ul>
 *
 * <h2>Authentication mechanism</h2>
 * <p>JWT Bearer tokens are used for stateless authentication. On login, a
 * signed JWT is returned. Subsequent requests must include that token in the
 * {@code Authorization: Bearer <token>} header.</p>
 */
@SpringBootApplication
public class RoleBasedAccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoleBasedAccessApplication.class, args);
    }
}
