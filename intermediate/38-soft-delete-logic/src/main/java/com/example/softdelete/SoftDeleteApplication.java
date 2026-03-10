package com.example.softdelete;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Soft Delete Logic mini-project.
 *
 * <p>This application demonstrates how to implement <strong>logical (soft) deletions</strong>
 * in Spring Boot using two Hibernate / JPA annotations:</p>
 * <ul>
 *   <li>{@code @SQLDelete} – overrides the default {@code DELETE} SQL statement with a custom
 *       {@code UPDATE} that sets a {@code deleted} flag instead of removing the row.</li>
 *   <li>{@code @SQLRestriction} (Hibernate 6+ replacement for the deprecated {@code @Where}) –
 *       automatically appends a {@code WHERE deleted = false} clause to every Hibernate query,
 *       so soft-deleted records are transparently invisible to normal read operations.</li>
 * </ul>
 *
 * <h2>Why soft deletes?</h2>
 * <p>Hard deletes are permanent and cannot be undone. Soft deletes let you preserve data for
 * auditing, compliance, or recovery purposes while keeping the entity invisible in normal
 * application queries.</p>
 */
@SpringBootApplication
public class SoftDeleteApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoftDeleteApplication.class, args);
    }
}
