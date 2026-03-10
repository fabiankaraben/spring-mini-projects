package com.example.jpaauditing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Abstract base class that provides automatic audit timestamp fields to every
 * entity that extends it.
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>{@code @MappedSuperclass} — tells JPA to include the fields declared here
 *       in the database table of every subclass entity, without creating a separate
 *       table for {@code Auditable} itself.</li>
 *   <li>{@code @EntityListeners(AuditingEntityListener.class)} — registers Spring
 *       Data JPA's built-in JPA lifecycle callback listener on this class hierarchy.
 *       The {@code AuditingEntityListener} intercepts {@code @PrePersist} and
 *       {@code @PreUpdate} events and sets the annotated timestamp fields.</li>
 *   <li>{@code @CreatedDate} — instructs the listener to populate {@code createdAt}
 *       once, at the moment the entity is first saved (INSERT).</li>
 *   <li>{@code @LastModifiedDate} — instructs the listener to update
 *       {@code updatedAt} on every save (INSERT and UPDATE).</li>
 * </ol>
 *
 * <p>All concrete entities (e.g. {@link Article}) extend this class so they
 * automatically inherit these audit columns without duplicating any code.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    /**
     * Timestamp of when this entity was first persisted in the database.
     *
     * <p>{@code updatable = false} prevents Hibernate from including this column in
     * UPDATE statements — once set, the creation timestamp never changes.
     *
     * <p>{@code nullable = false} enforces a NOT NULL constraint at the DB level.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp of the most recent update to this entity.
     *
     * <p>Spring Data JPA's {@code AuditingEntityListener} sets this field both on
     * INSERT (same moment as {@code createdAt}) and on every subsequent UPDATE,
     * always using the current UTC instant.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    // Accessors — no setters exposed because these fields are managed exclusively
    // by the JPA Auditing infrastructure, never set manually by application code.
    // -------------------------------------------------------------------------

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
