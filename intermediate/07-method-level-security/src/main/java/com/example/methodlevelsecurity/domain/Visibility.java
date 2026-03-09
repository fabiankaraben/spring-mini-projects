package com.example.methodlevelsecurity.domain;

/**
 * Visibility level of a {@link Document}.
 *
 * <p>This enum is used by {@code @PostFilter} in the document service to
 * automatically remove documents from a returned list when the caller does
 * not have permission to see them:</p>
 * <ul>
 *   <li>{@link #PUBLIC}  – visible to any authenticated user.</li>
 *   <li>{@link #PRIVATE} – visible only to the owning user, moderators, and admins.</li>
 * </ul>
 */
public enum Visibility {

    /**
     * The document is readable by all authenticated users regardless of role.
     */
    PUBLIC,

    /**
     * The document is readable only by its owner, moderators, and administrators.
     */
    PRIVATE
}
