package com.example.reactiver2dbc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;

/**
 * Spring Data R2DBC configuration class.
 *
 * <p>{@link EnableR2dbcAuditing} activates Spring Data's auditing infrastructure for
 * R2DBC entities. When enabled, Spring Data automatically populates fields annotated
 * with:
 * <ul>
 *   <li>{@code @CreatedDate} — set once on the first {@code save()} call (INSERT).</li>
 *   <li>{@code @LastModifiedDate} — updated on every {@code save()} call (INSERT or UPDATE).</li>
 * </ul>
 *
 * <p>Without this annotation, those fields would remain {@code null} even if the
 * annotations are present on the entity class. The entity must also implement
 * {@code Persistable} or use {@code @Id} with {@code null} for new entities so Spring
 * Data can distinguish between INSERT and UPDATE operations.
 *
 * <p>How Spring Data auditing works with R2DBC:
 * <ol>
 *   <li>Before saving, Spring Data checks if the entity is "new" (id is null → INSERT)
 *       or "existing" (id is non-null → UPDATE).</li>
 *   <li>For a new entity, it sets both {@code @CreatedDate} and {@code @LastModifiedDate}.</li>
 *   <li>For an existing entity, it only updates {@code @LastModifiedDate}.</li>
 *   <li>The actual timestamp is provided by a {@code ReactiveAuditorAware} bean (if
 *       auditing by user is needed) or simply by {@code Clock} for date/time fields.</li>
 * </ol>
 */
@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig {
    // No additional beans needed here.
    // Spring Boot auto-configures the R2DBC connection factory from application.yml.
    // @EnableR2dbcAuditing is the only reason this class exists.
}
