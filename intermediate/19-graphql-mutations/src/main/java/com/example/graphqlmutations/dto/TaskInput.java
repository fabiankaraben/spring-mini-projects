package com.example.graphqlmutations.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object (DTO) representing the input payload for creating or
 * updating a {@link com.example.graphqlmutations.domain.Task}.
 *
 * <p>This class corresponds to the {@code TaskInput} input type defined in
 * {@code schema.graphqls}. Spring for GraphQL deserializes the GraphQL
 * {@code input} argument into this object when the mutation argument is
 * annotated with {@code @Argument} in the controller.
 *
 * <p>Validation constraints are applied here so the service layer always
 * receives valid data. Validation failures are surfaced as GraphQL errors.
 */
public class TaskInput {

    /**
     * Title of the task.
     * {@code @NotBlank} ensures the title is not null, empty, or whitespace-only.
     */
    @NotBlank(message = "Task title must not be blank")
    private String title;

    /**
     * Optional detailed description of the work to be done.
     */
    private String description;

    /**
     * Priority level for the task. 1 is the highest priority, 5 is the lowest.
     * Defaults to 3 (normal priority).
     * {@code @Min} and {@code @Max} enforce the valid range.
     */
    @Min(value = 1, message = "Priority must be at least 1 (highest)")
    @Max(value = 5, message = "Priority must be at most 5 (lowest)")
    private int priority = 3;

    /**
     * The ID of the project this task belongs to.
     * {@code @NotNull} ensures a project ID is always provided.
     */
    @NotNull(message = "Project ID must not be null")
    private Long projectId;

    /** Default no-arg constructor required for Jackson deserialization. */
    public TaskInput() {
    }

    /**
     * Convenience constructor used in unit tests.
     *
     * @param title       the task title
     * @param description the optional description
     * @param priority    priority from 1 (highest) to 5 (lowest)
     * @param projectId   the ID of the owning project
     */
    public TaskInput(String title, String description, int priority, Long projectId) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.projectId = projectId;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

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

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
