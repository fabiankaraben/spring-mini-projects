package com.example.entitylifecycle.listener;

import com.example.entitylifecycle.entity.Product;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * External JPA entity listener for {@link Product} entities.
 *
 * <p>This class demonstrates the <em>external listener</em> approach to handling
 * JPA lifecycle events. Instead of adding callback methods directly to the entity
 * class, you register a separate listener class via {@code @EntityListeners} on
 * the entity. This approach is ideal when:
 * <ul>
 *   <li>You want to keep the entity class clean (single responsibility).</li>
 *   <li>The listener logic needs access to Spring beans (requires a CDI/Spring proxy
 *       trick — see note below).</li>
 *   <li>Multiple entity types share the same listener behaviour.</li>
 * </ul>
 *
 * <p><b>How JPA instantiates listeners:</b>
 * JPA creates a new instance of this class for each entity operation. The class
 * must therefore have a public no-arg constructor (provided by the compiler as
 * the default). It is <em>not</em> a Spring-managed bean by default, so we cannot
 * directly inject Spring services here. For simplicity this listener uses only the
 * SLF4J logger. If you need Spring beans inside a listener, the canonical solution
 * is to use {@code @Configurable} or look up the bean from a static
 * {@code ApplicationContext} holder.
 *
 * <p>The callback methods must:
 * <ul>
 *   <li>Be {@code void}.</li>
 *   <li>Accept a single parameter of the entity type (or a common supertype).</li>
 *   <li>Not throw checked exceptions.</li>
 * </ul>
 */
public class ProductAuditListener {

    private static final Logger log = LoggerFactory.getLogger(ProductAuditListener.class);

    /**
     * Fired by Hibernate immediately after a new {@link Product} row is committed
     * to the database.
     *
     * <p>At this point the entity has an ID assigned by the database. This hook
     * is suitable for "after-commit" side-effects such as:
     * <ul>
     *   <li>Logging a business event to an audit log.</li>
     *   <li>Sending a notification (if Spring beans were available here).</li>
     *   <li>Publishing a domain event.</li>
     * </ul>
     *
     * @param product the newly persisted product
     */
    @PostPersist
    public void onPostPersist(Product product) {
        log.info("[AUDIT] Product CREATED: id={}, name='{}', slug='{}', price={}",
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getPrice());
    }

    /**
     * Fired by Hibernate immediately before Hibernate issues the SQL UPDATE
     * statement for a dirty (modified) {@link Product} entity.
     *
     * <p>Used here to log which fields are being updated. In a real application
     * this hook could persist an audit-trail record to a separate table.
     *
     * @param product the product about to be updated
     */
    @PreUpdate
    public void onPreUpdate(Product product) {
        log.info("[AUDIT] Product UPDATING: id={}, name='{}', updatedAt={}",
                product.getId(),
                product.getName(),
                product.getUpdatedAt());
    }

    /**
     * Fired by Hibernate immediately after a {@link Product} row is deleted from
     * the database.
     *
     * <p>The entity object is still available in memory at this point (with its
     * last-known field values), which makes it useful for logging or sending
     * a notification with details of what was deleted.
     *
     * @param product the deleted product
     */
    @PostRemove
    public void onPostRemove(Product product) {
        log.info("[AUDIT] Product DELETED: id={}, name='{}'",
                product.getId(),
                product.getName());
    }
}
