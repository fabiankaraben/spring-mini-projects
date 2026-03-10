package com.example.reactivemongodbapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;

/**
 * MongoDB configuration class that enables reactive auditing.
 *
 * <p>{@link EnableReactiveMongoAuditing} activates Spring Data's auditing infrastructure
 * for reactive MongoDB repositories. Without this annotation, the {@code @CreatedDate}
 * and {@code @LastModifiedDate} annotations on the {@link com.example.reactivemongodbapi.domain.Book}
 * entity would be silently ignored and the timestamps would remain {@code null}.
 *
 * <p><strong>How reactive auditing works:</strong>
 * <ol>
 *   <li>Spring Data registers an {@code AuditingEntityCallback} (reactive version of the
 *       traditional {@code AuditingEntityListener} used in JPA).</li>
 *   <li>Before each reactive {@code save()} call, the callback inspects the entity for
 *       {@code @CreatedDate} and {@code @LastModifiedDate} fields.</li>
 *   <li>On first insert ({@code id == null}), both fields are set to the current UTC
 *       {@link java.time.Instant}.</li>
 *   <li>On subsequent saves (update, {@code id != null}), only {@code @LastModifiedDate}
 *       is refreshed; {@code @CreatedDate} is preserved.</li>
 * </ol>
 *
 * <p><strong>Why a separate config class?</strong><br>
 * Keeping infrastructure concerns (auditing setup, codec configuration, index management)
 * separate from the main application class ({@link com.example.reactivemongodbapi.ReactiveMongodbApiApplication})
 * follows the Single Responsibility Principle and makes the configuration easier to test
 * and evolve independently.
 */
@Configuration
@EnableReactiveMongoAuditing
public class MongoConfig {
    // No bean definitions needed here — @EnableReactiveMongoAuditing registers all
    // required auditing infrastructure beans automatically via Spring Boot's
    // auto-configuration mechanism.
}
