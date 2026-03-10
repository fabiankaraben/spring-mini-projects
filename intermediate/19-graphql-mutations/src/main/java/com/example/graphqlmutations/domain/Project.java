package com.example.graphqlmutations.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a project in the task manager domain.
 *
 * <p>A project acts as a container that groups related {@link Task}s together.
 * It maps to the {@code projects} table in PostgreSQL.
 *
 * <p>In the GraphQL schema, {@code Project} is a type whose {@code tasks} field
 * allows clients to fetch all tasks nested within a single query/mutation response,
 * demonstrating GraphQL's ability to return deeply nested object graphs in one request.
 *
 * <p>The relationship with {@link Task} is one-to-many: one project can have many tasks,
 * and each task belongs to exactly one project.
 */
@Entity
@Table(name = "projects")
public class Project {

    /**
     * Primary key – auto-incremented by the PostgreSQL sequence.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the project. Cannot be null.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Optional description providing more context about the project's goals.
     */
    @Column
    private String description;

    /**
     * All tasks belonging to this project.
     *
     * <p>{@code CascadeType.ALL} means JPA operations (persist, merge, remove, etc.)
     * are cascaded to tasks. When a project is deleted, all its tasks are deleted too.
     * {@code orphanRemoval = true} ensures that tasks removed from this list are
     * automatically deleted from the database.
     *
     * <p>{@code mappedBy = "project"} tells JPA that the {@code Task.project} field
     * owns the foreign-key relationship (i.e., the {@code project_id} column is in
     * the {@code tasks} table, not in {@code projects}).
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();

    /** Required no-arg constructor for JPA. */
    protected Project() {
    }

    /**
     * Creates a new Project with the required fields.
     *
     * @param name        the project name
     * @param description an optional description (may be {@code null})
     */
    public Project(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Task> getTasks() {
        return tasks;
    }
}
