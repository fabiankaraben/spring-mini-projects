package com.example.graphqlmutations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity representing a task in the task manager domain.
 *
 * <p>A task is a unit of work that belongs to a {@link Project}. It maps to the
 * {@code tasks} table in PostgreSQL. Tasks have a status lifecycle managed through
 * dedicated GraphQL mutations that demonstrate state-change operations.
 *
 * <p>The status lifecycle is:
 * <pre>
 *   TODO  ──►  IN_PROGRESS  ──►  DONE
 * </pre>
 *
 * <p>Key GraphQL mutation operations for tasks:
 * <ul>
 *   <li>{@code createTask} – creates a task with status {@link TaskStatus#TODO}.</li>
 *   <li>{@code updateTask} – updates the task's title, description, or priority.</li>
 *   <li>{@code startTask} – transitions a task from {@link TaskStatus#TODO} to
 *       {@link TaskStatus#IN_PROGRESS}.</li>
 *   <li>{@code completeTask} – transitions a task from any status to
 *       {@link TaskStatus#DONE}.</li>
 *   <li>{@code deleteTask} – removes a task by ID.</li>
 * </ul>
 */
@Entity
@Table(name = "tasks")
public class Task {

    /**
     * Primary key – auto-incremented by the PostgreSQL sequence.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Title of the task. Cannot be null.
     */
    @Column(nullable = false)
    private String title;

    /**
     * Optional description with details about what needs to be done.
     */
    @Column
    private String description;

    /**
     * Current lifecycle status of the task.
     *
     * <p>{@code EnumType.STRING} stores the enum constant name (e.g., "TODO")
     * as a VARCHAR in the database instead of an ordinal integer. String storage
     * is preferred because it is resilient to reordering enum constants and makes
     * the database content human-readable.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    /**
     * Priority level from 1 (highest) to 5 (lowest). Defaults to 3 (normal).
     *
     * <p>This field is intentionally kept as a simple integer (not an enum) to
     * illustrate using a numeric value in GraphQL input types.
     */
    @Column(nullable = false)
    private int priority;

    /**
     * The project this task belongs to.
     *
     * <p>{@link FetchType#LAZY} defers loading the parent {@link Project} until
     * {@code getProject()} is explicitly called. This is efficient because GraphQL
     * only fetches nested fields that the client requests — if a query only asks
     * for task fields, the project won't be loaded at all.
     *
     * <p>{@code @JoinColumn(name = "project_id")} specifies the foreign-key
     * column name in the {@code tasks} table.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Required no-arg constructor for JPA. */
    protected Task() {
    }

    /**
     * Creates a new Task with the required fields.
     * The status defaults to {@link TaskStatus#TODO} when a task is first created.
     *
     * @param title       the task title
     * @param description an optional description (may be {@code null})
     * @param priority    priority level from 1 (highest) to 5 (lowest)
     * @param project     the project this task belongs to
     */
    public Task(String title, String description, int priority, Project project) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.project = project;
        // New tasks always start in the TODO state
        this.status = TaskStatus.TODO;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }
}
