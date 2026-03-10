package com.example.jpaauditing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing configuration class.
 *
 * <p>{@code @EnableJpaAuditing} is the single annotation that turns on Spring Data's
 * auditing infrastructure for the entire application context. Once active, Spring Data
 * registers an {@code AuditingBeanFactoryPostProcessor} and an
 * {@code AuditingEntityListener} bean that listen for JPA lifecycle callbacks
 * ({@code @PrePersist} and {@code @PreUpdate}) and automatically populate fields
 * annotated with:
 * <ul>
 *   <li>{@code @CreatedDate}      — set once when the entity is first persisted.</li>
 *   <li>{@code @LastModifiedDate} — updated on every subsequent {@code save()} call.</li>
 *   <li>{@code @CreatedBy}        — requires an {@code AuditorAware} bean (not used here).</li>
 *   <li>{@code @LastModifiedBy}   — requires an {@code AuditorAware} bean (not used here).</li>
 * </ul>
 *
 * <p>This configuration is intentionally kept in a dedicated class (rather than placed
 * directly on the main {@code @SpringBootApplication} class) to keep responsibilities
 * separated and to make it easy to extend — for example, adding an
 * {@code AuditorAware} bean later for user-based auditing.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // No beans required here for timestamp-only auditing.
    // To also audit the "who" (created-by / last-modified-by), declare an
    // AuditorAware<String> bean in this class.
}
