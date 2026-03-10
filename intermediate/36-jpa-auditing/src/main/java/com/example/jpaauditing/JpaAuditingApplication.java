package com.example.jpaauditing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the JPA Auditing mini-project.
 *
 * <p>JPA Auditing is a Spring Data feature that automatically populates
 * audit-related fields on entities (such as creation timestamp and last-modified
 * timestamp) without any manual effort in service or repository code.
 *
 * <p>The key pieces wired together in this application are:
 * <ul>
 *   <li>{@code @EnableJpaAuditing} — activates the Spring Data auditing
 *       infrastructure (declared in {@link JpaAuditingConfig}).</li>
 *   <li>{@code @EntityListeners(AuditingEntityListener.class)} — attaches the
 *       JPA lifecycle callback listener to each audited entity.</li>
 *   <li>{@code @CreatedDate} / {@code @LastModifiedDate} — marks the fields that
 *       Spring Data must populate automatically.</li>
 * </ul>
 */
@SpringBootApplication
public class JpaAuditingApplication {

    public static void main(String[] args) {
        SpringApplication.run(JpaAuditingApplication.class, args);
    }
}
