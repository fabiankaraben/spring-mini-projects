package com.example.graphqlmutations.domain;

/**
 * Represents the lifecycle status of a {@link Task}.
 *
 * <p>The valid state transitions are:
 * <pre>
 *   TODO ──► IN_PROGRESS ──► DONE
 * </pre>
 *
 * <p>This enum is stored in the database as a {@code VARCHAR} via JPA's
 * {@code @Enumerated(EnumType.STRING)} annotation in {@link Task}, which makes
 * the stored values human-readable and resilient to reordering of enum constants.
 *
 * <p>In the GraphQL schema, this enum is declared as a GraphQL {@code enum} type,
 * so clients can only supply one of these three string values in mutation arguments.
 */
public enum TaskStatus {

    /**
     * The task has been created but not yet started.
     * This is the default status when a task is first created.
     */
    TODO,

    /**
     * The task is currently being worked on.
     */
    IN_PROGRESS,

    /**
     * The task has been completed.
     */
    DONE
}
