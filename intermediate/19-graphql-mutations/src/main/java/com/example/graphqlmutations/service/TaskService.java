package com.example.graphqlmutations.service;

import com.example.graphqlmutations.domain.Project;
import com.example.graphqlmutations.domain.Task;
import com.example.graphqlmutations.domain.TaskStatus;
import com.example.graphqlmutations.dto.TaskInput;
import com.example.graphqlmutations.repository.ProjectRepository;
import com.example.graphqlmutations.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for task-related business logic.
 *
 * <p>This service is the heart of the mini-project's mutation demonstrations.
 * It provides a rich set of write operations that showcase different kinds of
 * GraphQL mutations:
 * <ul>
 *   <li><b>Standard CRUD mutations</b>: {@code createTask}, {@code updateTask},
 *       {@code deleteTask} – the bread-and-butter of any GraphQL API.</li>
 *   <li><b>State-transition mutations</b>: {@code startTask}, {@code completeTask},
 *       {@code reopenTask} – mutations that encapsulate domain-specific state changes
 *       rather than generic field updates. These are idiomatic GraphQL: instead of
 *       a generic {@code updateTask(status: IN_PROGRESS)}, you expose intent-revealing
 *       operations like {@code startTask(id)} that enforce business rules.</li>
 * </ul>
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    /**
     * @param taskRepository    repository for task persistence operations
     * @param projectRepository repository used to look up the project referenced in input DTOs
     */
    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    // ── Read operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all tasks from the database.
     *
     * @return list of all tasks (empty list if none exist)
     */
    @Transactional(readOnly = true)
    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    /**
     * Retrieve a single task by its primary key.
     *
     * @param id the task's primary key
     * @return an {@link Optional} containing the task, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<Task> findById(Long id) {
        return taskRepository.findById(id);
    }

    /**
     * Retrieve all tasks belonging to a specific project.
     *
     * @param projectId the primary key of the project
     * @return list of tasks in that project
     */
    @Transactional(readOnly = true)
    public List<Task> findByProjectId(Long projectId) {
        return taskRepository.findByProjectId(projectId);
    }

    /**
     * Retrieve all tasks with a specific status.
     *
     * @param status the status to filter by
     * @return list of tasks in that status
     */
    @Transactional(readOnly = true)
    public List<Task> findByStatus(TaskStatus status) {
        return taskRepository.findByStatus(status);
    }

    /**
     * Retrieve all tasks in a project filtered by status.
     *
     * @param projectId the primary key of the project
     * @param status    the status to filter by
     * @return filtered list of tasks
     */
    @Transactional(readOnly = true)
    public List<Task> findByProjectIdAndStatus(Long projectId, TaskStatus status) {
        return taskRepository.findByProjectIdAndStatus(projectId, status);
    }

    // ── Standard CRUD mutations ───────────────────────────────────────────────────

    /**
     * Create and persist a new task.
     *
     * <p>The new task is automatically assigned {@link TaskStatus#TODO} status by the
     * {@link Task} constructor, enforcing the business rule that tasks always start
     * in the TODO state.
     *
     * @param input the task data from the GraphQL mutation argument
     * @return the persisted task with its generated primary key
     * @throws IllegalArgumentException if no project exists with the given {@code projectId}
     */
    @Transactional
    public Task create(TaskInput input) {
        // Resolve the project reference – fail fast with a descriptive error if not found
        Project project = projectRepository.findById(input.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project not found with id: " + input.getProjectId()));

        Task task = new Task(
                input.getTitle(),
                input.getDescription(),
                input.getPriority(),
                project
        );
        return taskRepository.save(task);
    }

    /**
     * Update an existing task's mutable fields (title, description, priority).
     *
     * <p>Note that status is NOT updated through this method — status changes
     * are handled by dedicated state-transition mutations ({@link #startTask},
     * {@link #completeTask}, {@link #reopenTask}). This separation makes the
     * API explicit about intent and allows the server to enforce state-transition
     * business rules.
     *
     * @param id    the primary key of the task to update
     * @param input the new field values (projectId is used to re-assign the task)
     * @return an {@link Optional} with the updated task, or empty if not found
     * @throws IllegalArgumentException if the referenced project does not exist
     */
    @Transactional
    public Optional<Task> update(Long id, TaskInput input) {
        Optional<Task> existing = taskRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Task task = existing.get();
        task.setTitle(input.getTitle());
        task.setDescription(input.getDescription());
        task.setPriority(input.getPriority());

        // Re-resolve project if a (potentially different) project ID is supplied
        Project project = projectRepository.findById(input.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project not found with id: " + input.getProjectId()));
        task.setProject(project);

        // JPA dirty-checking issues the SQL UPDATE on transaction commit
        return Optional.of(task);
    }

    /**
     * Delete a task by primary key.
     *
     * @param id the primary key of the task to delete
     * @return {@code true} if the task existed and was deleted; {@code false} if not found
     */
    @Transactional
    public boolean deleteById(Long id) {
        if (!taskRepository.existsById(id)) {
            return false;
        }
        taskRepository.deleteById(id);
        return true;
    }

    // ── State-transition mutations ─────────────────────────────────────────────────
    // These mutations demonstrate a key GraphQL design principle: rather than
    // exposing generic "update status to X" operations, we expose intent-revealing
    // mutations that encode business rules and transitions explicitly.

    /**
     * Transition a task to {@link TaskStatus#IN_PROGRESS}.
     *
     * <p>This mutation represents the action of a developer starting work on a task.
     * It enforces the business rule that only TODO tasks can be started.
     *
     * @param id the primary key of the task to start
     * @return an {@link Optional} with the updated task, or empty if not found
     * @throws IllegalStateException if the task is not in TODO status
     */
    @Transactional
    public Optional<Task> startTask(Long id) {
        Optional<Task> existing = taskRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Task task = existing.get();

        // Enforce the state-transition business rule: only TODO tasks can be started.
        // Returning an error here prevents invalid state machines (e.g., starting a
        // task that's already DONE) and surfaces a meaningful message to the client.
        if (task.getStatus() != TaskStatus.TODO) {
            throw new IllegalStateException(
                    "Task " + id + " cannot be started because its current status is "
                            + task.getStatus() + ". Only TODO tasks can be started.");
        }

        task.setStatus(TaskStatus.IN_PROGRESS);
        return Optional.of(task);
    }

    /**
     * Transition a task to {@link TaskStatus#DONE}.
     *
     * <p>A task can be completed from any status — whether it was TODO or IN_PROGRESS.
     * This models the real-world scenario where a task might be completed directly
     * without an explicit "start" step.
     *
     * @param id the primary key of the task to complete
     * @return an {@link Optional} with the updated task, or empty if not found
     * @throws IllegalStateException if the task is already DONE
     */
    @Transactional
    public Optional<Task> completeTask(Long id) {
        Optional<Task> existing = taskRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Task task = existing.get();

        // Completing an already-done task is a no-op business-rule violation
        if (task.getStatus() == TaskStatus.DONE) {
            throw new IllegalStateException(
                    "Task " + id + " is already completed.");
        }

        task.setStatus(TaskStatus.DONE);
        return Optional.of(task);
    }

    /**
     * Reopen a completed task by transitioning it back to {@link TaskStatus#TODO}.
     *
     * <p>This mutation demonstrates a backwards state transition — useful when
     * a task was marked as done but needs to be revisited (e.g., bug was found).
     *
     * @param id the primary key of the task to reopen
     * @return an {@link Optional} with the updated task, or empty if not found
     * @throws IllegalStateException if the task is not in DONE status
     */
    @Transactional
    public Optional<Task> reopenTask(Long id) {
        Optional<Task> existing = taskRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Task task = existing.get();

        // Only DONE tasks can be reopened – this prevents confusion about task states
        if (task.getStatus() != TaskStatus.DONE) {
            throw new IllegalStateException(
                    "Task " + id + " cannot be reopened because its current status is "
                            + task.getStatus() + ". Only DONE tasks can be reopened.");
        }

        task.setStatus(TaskStatus.TODO);
        return Optional.of(task);
    }
}
