package com.example.entitylifecycle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Entity Lifecycle Events mini-project.
 *
 * <p>This application demonstrates how JPA entity lifecycle event callbacks
 * can be used to run business logic automatically at specific points in an
 * entity's persistence lifecycle — without any manual calls in services.
 *
 * <p>The five standard JPA lifecycle callback annotations used in this project:
 * <ul>
 *   <li>{@code @PrePersist}   — fires just before a new entity is INSERTed.
 *       Used here to generate a URL-friendly slug from the product name and
 *       to stamp the {@code createdAt} timestamp.</li>
 *   <li>{@code @PostPersist}  — fires just after the INSERT is committed.
 *       Used here to log a business event ("product created").</li>
 *   <li>{@code @PreUpdate}    — fires just before an UPDATE is issued.
 *       Used here to refresh the {@code updatedAt} timestamp.</li>
 *   <li>{@code @PostLoad}     — fires after an entity is loaded from the DB.
 *       Used here to compute a transient {@code discountedPrice} field that
 *       is never stored — it is always recalculated on load.</li>
 *   <li>{@code @PostRemove}   — fires just after a DELETE is committed.
 *       Used here to log a business event ("product deleted").</li>
 * </ul>
 *
 * <p>Key design choices:
 * <ul>
 *   <li>Lifecycle callbacks are declared directly on the entity class using
 *       method annotations (the simplest and most common approach).</li>
 *   <li>An {@code @EntityListeners} external listener class is also provided
 *       ({@link com.example.entitylifecycle.listener.ProductAuditListener})
 *       to show the alternative — separating lifecycle logic from the entity.</li>
 * </ul>
 */
@SpringBootApplication
public class EntityLifecycleEventsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntityLifecycleEventsApplication.class, args);
    }
}
