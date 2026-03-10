package com.example.optimisticlocking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Optimistic Locking mini-project.
 *
 * <h2>What this project demonstrates</h2>
 * <p>This application shows how JPA's {@code @Version} annotation prevents the
 * "lost update" problem that occurs when two concurrent transactions read an entity,
 * both modify it, and both try to save it.  Without optimistic locking, the second
 * writer silently overwrites the first writer's changes.  With {@code @Version},
 * the second writer receives an {@link jakarta.persistence.OptimisticLockException}
 * instead, and the application can retry or report the conflict to the client.</p>
 *
 * <h2>Key concepts</h2>
 * <ul>
 *   <li><strong>{@code @Version}</strong> – Hibernate adds a {@code WHERE version = ?}
 *       clause to every UPDATE.  If the version in the database has changed since the
 *       entity was loaded, the WHERE clause matches zero rows and Hibernate throws
 *       {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.</li>
 *   <li><strong>Conflict response</strong> – the global exception handler translates
 *       the exception into an HTTP 409 Conflict so REST clients can handle it.</li>
 *   <li><strong>Version exposure</strong> – the version number is included in every
 *       response DTO so clients can pass it back in update requests, enabling the
 *       controller to detect stale reads.</li>
 * </ul>
 */
@SpringBootApplication
public class OptimisticLockingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OptimisticLockingApplication.class, args);
    }
}
