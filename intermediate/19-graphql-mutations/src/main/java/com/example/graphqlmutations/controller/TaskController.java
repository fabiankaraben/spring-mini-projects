package com.example.graphqlmutations.controller;

import com.example.graphqlmutations.domain.Task;
import com.example.graphqlmutations.domain.TaskStatus;
import com.example.graphqlmutations.dto.TaskInput;
import com.example.graphqlmutations.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL controller (resolver) for {@link Task}-related queries and mutations.
 *
 * <p>This controller showcases both standard CRUD mutations and state-transition
 * mutations — the core focus of this mini-project. The state-transition mutations
 * ({@code startTask}, {@code completeTask}, {@code reopenTask}) demonstrate how
 * GraphQL mutations can model domain-specific operations with business rules, rather
 * than generic "update this field" operations.
 *
 * <p>Compare the two approaches:
 * <ul>
 *   <li><b>Generic update</b>: {@code updateTask(id: 1, input: { status: IN_PROGRESS })}
 *       — clients must know valid status values and transitions.</li>
 *   <li><b>Intent-revealing mutation</b>: {@code startTask(id: 1)}
 *       — the operation name communicates intent; the server enforces business rules.</li>
 * </ul>
 */
@Controller
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // ── Query handlers ────────────────────────────────────────────────────────────

    /**
     * Resolves the {@code tasks} query – returns all tasks.
     *
     * @return list of all tasks
     */
    @QueryMapping
    public List<Task> tasks() {
        return taskService.findAll();
    }

    /**
     * Resolves the {@code task(id: ID!)} query – returns one task by ID.
     *
     * @param id the task's primary key
     * @return the task, or {@code null} if not found
     */
    @QueryMapping
    public Task task(@Argument Long id) {
        return taskService.findById(id).orElse(null);
    }

    /**
     * Resolves the {@code tasksByProject(projectId: ID!)} query.
     *
     * @param projectId the ID of the project whose tasks to retrieve
     * @return list of tasks in that project
     */
    @QueryMapping
    public List<Task> tasksByProject(@Argument Long projectId) {
        return taskService.findByProjectId(projectId);
    }

    /**
     * Resolves the {@code tasksByStatus(status: TaskStatus!)} query.
     *
     * @param status the status to filter by (TODO, IN_PROGRESS, or DONE)
     * @return list of tasks in that status
     */
    @QueryMapping
    public List<Task> tasksByStatus(@Argument TaskStatus status) {
        return taskService.findByStatus(status);
    }

    /**
     * Resolves the {@code tasksByProjectAndStatus(projectId: ID!, status: TaskStatus!)} query.
     * Useful for building kanban-style boards: fetch all IN_PROGRESS tasks in a project.
     *
     * @param projectId the ID of the project
     * @param status    the status to filter by
     * @return filtered list of tasks
     */
    @QueryMapping
    public List<Task> tasksByProjectAndStatus(@Argument Long projectId, @Argument TaskStatus status) {
        return taskService.findByProjectIdAndStatus(projectId, status);
    }

    // ── Standard CRUD mutation handlers ──────────────────────────────────────────

    /**
     * Resolves the {@code createTask(input: TaskInput!)} mutation.
     *
     * <p>The new task will always have {@code status: TODO} regardless of any
     * value provided in the input (status is not part of {@code TaskInput}).
     * This enforces the business rule that all tasks start in the TODO state.
     *
     * @param input the deserialized GraphQL {@code TaskInput} argument
     * @return the newly created task with its generated ID and TODO status
     */
    @MutationMapping
    public Task createTask(@Argument @Valid TaskInput input) {
        return taskService.create(input);
    }

    /**
     * Resolves the {@code updateTask(id: ID!, input: TaskInput!)} mutation.
     *
     * <p>Updates the task's title, description, priority, and project reference.
     * Status is intentionally excluded — use the dedicated state-transition
     * mutations to change a task's status.
     *
     * @param id    the ID of the task to update
     * @param input the new field values
     * @return the updated task, or {@code null} if not found
     */
    @MutationMapping
    public Task updateTask(@Argument Long id, @Argument @Valid TaskInput input) {
        return taskService.update(id, input).orElse(null);
    }

    /**
     * Resolves the {@code deleteTask(id: ID!)} mutation.
     *
     * @param id the ID of the task to delete
     * @return {@code true} if deleted; {@code false} if not found
     */
    @MutationMapping
    public boolean deleteTask(@Argument Long id) {
        return taskService.deleteById(id);
    }

    // ── State-transition mutation handlers ────────────────────────────────────────
    // These mutations are the primary focus of this mini-project.
    // Each one represents a specific, intent-revealing state change in the task lifecycle.

    /**
     * Resolves the {@code startTask(id: ID!)} mutation.
     *
     * <p>Transitions the task status from {@code TODO} to {@code IN_PROGRESS}.
     * Returns a GraphQL error if the task is not currently in {@code TODO} status.
     *
     * @param id the ID of the task to start
     * @return the updated task with {@code IN_PROGRESS} status, or {@code null} if not found
     */
    @MutationMapping
    public Task startTask(@Argument Long id) {
        return taskService.startTask(id).orElse(null);
    }

    /**
     * Resolves the {@code completeTask(id: ID!)} mutation.
     *
     * <p>Transitions the task status to {@code DONE} from either {@code TODO} or
     * {@code IN_PROGRESS}. Returns a GraphQL error if the task is already {@code DONE}.
     *
     * @param id the ID of the task to complete
     * @return the updated task with {@code DONE} status, or {@code null} if not found
     */
    @MutationMapping
    public Task completeTask(@Argument Long id) {
        return taskService.completeTask(id).orElse(null);
    }

    /**
     * Resolves the {@code reopenTask(id: ID!)} mutation.
     *
     * <p>Transitions a {@code DONE} task back to {@code TODO}. This is useful when
     * a completed task needs to be revisited (e.g., a bug was found in the solution).
     * Returns a GraphQL error if the task is not currently in {@code DONE} status.
     *
     * @param id the ID of the task to reopen
     * @return the updated task with {@code TODO} status, or {@code null} if not found
     */
    @MutationMapping
    public Task reopenTask(@Argument Long id) {
        return taskService.reopenTask(id).orElse(null);
    }
}
