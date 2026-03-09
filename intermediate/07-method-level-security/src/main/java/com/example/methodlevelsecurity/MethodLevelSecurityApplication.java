package com.example.methodlevelsecurity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Method Level Security mini-project.
 *
 * <p>This application demonstrates securing service-layer methods using
 * Spring Security's method-level annotations:</p>
 * <ul>
 *   <li>{@code @PreAuthorize}  – evaluates a SpEL expression <em>before</em> the method runs.</li>
 *   <li>{@code @PostAuthorize} – evaluates a SpEL expression <em>after</em> the method returns,
 *       allowing the return value ({@code returnObject}) to be inspected.</li>
 *   <li>{@code @PreFilter}     – filters a collection <em>argument</em> before the method runs.</li>
 *   <li>{@code @PostFilter}    – filters a collection <em>return value</em> after the method runs.</li>
 *   <li>{@code @Secured}       – simpler, role-only alternative to {@code @PreAuthorize}.</li>
 * </ul>
 *
 * <p>The domain is a <strong>Document Management API</strong>: users can create, read,
 * update and delete their own documents. Moderators and admins have elevated access.
 * All access decisions are enforced at the service layer, not just at the HTTP layer.</p>
 */
@SpringBootApplication
public class MethodLevelSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(MethodLevelSecurityApplication.class, args);
    }
}
