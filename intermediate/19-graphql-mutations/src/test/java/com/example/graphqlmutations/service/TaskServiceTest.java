package com.example.graphqlmutations.service;

import com.example.graphqlmutations.domain.Project;
import com.example.graphqlmutations.domain.Task;
import com.example.graphqlmutations.domain.TaskStatus;
import com.example.graphqlmutations.dto.TaskInput;
import com.example.graphqlmutations.repository.ProjectRepository;
import com.example.graphqlmutations.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TaskService}.
 *
 * <p>All repository dependencies are mocked with Mockito, so these tests run
 * without a database or Spring application context.
 *
 * <p>Key scenarios covered:
 * <ul>
 *   <li>Read operations delegate correctly to the repository.</li>
 *   <li>Create resolves the project by ID and saves the new task.</li>
 *   <li>Create throws {@link IllegalArgumentException} when the project is missing.</li>
 *   <li>Update applies field changes and handles the "not found" case.</li>
 *   <li>Delete returns the correct boolean and delegates to the repository.</li>
 *   <li>State-transition mutations (startTask, completeTask, reopenTask) enforce
 *       business rules and throw {@link IllegalStateException} on invalid transitions.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService unit tests")
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    /**
     * Mockito injects both mocks via the two-argument constructor of {@link TaskService}.
     */
    @InjectMocks
    private TaskService taskService;

    private Project sampleProject;
    private Task sampleTask;

    @BeforeEach
    void setUp() {
        sampleProject = new Project("Backend Refactor", "Refactor legacy services");
        // sampleTask starts with TODO status (set by Task constructor)
        sampleTask = new Task("Fix login bug", "Users can't log in on mobile", 1, sampleProject);
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns the list from the repository")
    void findAll_returnsTasks() {
        when(taskRepository.findAll()).thenReturn(List.of(sampleTask));

        List<Task> result = taskService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Fix login bug");
        verify(taskRepository).findAll();
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns Optional with task when found")
    void findById_returnsTask_whenFound() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        Optional<Task> result = taskService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Fix login bug");
    }

    @Test
    @DisplayName("findById returns empty Optional when not found")
    void findById_returnsEmpty_whenNotFound() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(taskService.findById(99L)).isEmpty();
    }

    // ── findByProjectId ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByProjectId delegates to repository with the correct projectId")
    void findByProjectId_delegatesToRepository() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(sampleTask));

        List<Task> result = taskService.findByProjectId(1L);

        assertThat(result).hasSize(1);
        verify(taskRepository).findByProjectId(1L);
    }

    // ── findByStatus ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByStatus delegates to repository with the correct status")
    void findByStatus_delegatesToRepository() {
        when(taskRepository.findByStatus(TaskStatus.TODO)).thenReturn(List.of(sampleTask));

        List<Task> result = taskService.findByStatus(TaskStatus.TODO);

        assertThat(result).hasSize(1);
        verify(taskRepository).findByStatus(TaskStatus.TODO);
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create resolves project, builds Task with TODO status, and calls save")
    void create_savesNewTask() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(sampleProject));
        when(taskRepository.save(any(Task.class))).thenReturn(sampleTask);

        TaskInput input = new TaskInput("Fix login bug", "Users can't log in", 1, 1L);
        Task result = taskService.create(input);

        assertThat(result.getTitle()).isEqualTo("Fix login bug");
        // Verify the correct status is set by the Task constructor (business rule)
        assertThat(result.getStatus()).isEqualTo(TaskStatus.TODO);
        verify(projectRepository).findById(1L);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    @DisplayName("create throws IllegalArgumentException when project is not found")
    void create_throwsException_whenProjectNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        TaskInput input = new TaskInput("Task", null, 3, 99L);

        // Verify that the service fails fast with a descriptive error message
        assertThatThrownBy(() -> taskService.create(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        // The task should NOT be saved if the project lookup fails
        verify(taskRepository, never()).save(any());
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update returns empty when task is not found")
    void update_returnsEmpty_whenNotFound() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Task> result = taskService.update(99L,
                new TaskInput("T", null, 3, 1L));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("update modifies the task fields when found")
    void update_modifiesFields_whenFound() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(sampleProject));

        TaskInput input = new TaskInput("Updated Title", "Updated description", 2, 1L);
        Optional<Task> result = taskService.update(1L, input);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Updated Title");
        assertThat(result.get().getDescription()).isEqualTo("Updated description");
        assertThat(result.get().getPriority()).isEqualTo(2);
    }

    @Test
    @DisplayName("update throws IllegalArgumentException when new project is not found")
    void update_throwsException_whenProjectNotFound() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(1L, new TaskInput("T", null, 3, 99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById returns false when task does not exist")
    void deleteById_returnsFalse_whenNotFound() {
        when(taskRepository.existsById(99L)).thenReturn(false);

        assertThat(taskService.deleteById(99L)).isFalse();
        verify(taskRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteById returns true and calls repository.deleteById when found")
    void deleteById_returnsTrue_andDeletes_whenFound() {
        when(taskRepository.existsById(1L)).thenReturn(true);

        assertThat(taskService.deleteById(1L)).isTrue();
        verify(taskRepository).deleteById(1L);
    }

    // ── startTask (state-transition mutation) ─────────────────────────────────────

    @Test
    @DisplayName("startTask transitions TODO task to IN_PROGRESS")
    void startTask_transitionsToInProgress() {
        // sampleTask starts with TODO status
        assertThat(sampleTask.getStatus()).isEqualTo(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        Optional<Task> result = taskService.startTask(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("startTask returns empty when task is not found")
    void startTask_returnsEmpty_whenNotFound() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(taskService.startTask(99L)).isEmpty();
    }

    @Test
    @DisplayName("startTask throws IllegalStateException when task is already IN_PROGRESS")
    void startTask_throwsException_whenAlreadyInProgress() {
        sampleTask.setStatus(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        assertThatThrownBy(() -> taskService.startTask(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_PROGRESS");
    }

    @Test
    @DisplayName("startTask throws IllegalStateException when task is DONE")
    void startTask_throwsException_whenDone() {
        sampleTask.setStatus(TaskStatus.DONE);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        assertThatThrownBy(() -> taskService.startTask(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DONE");
    }

    // ── completeTask (state-transition mutation) ──────────────────────────────────

    @Test
    @DisplayName("completeTask transitions TODO task directly to DONE")
    void completeTask_transitionsTodotoDone() {
        // Tasks can be completed directly from TODO (skipping IN_PROGRESS)
        assertThat(sampleTask.getStatus()).isEqualTo(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        Optional<Task> result = taskService.completeTask(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    @DisplayName("completeTask transitions IN_PROGRESS task to DONE")
    void completeTask_transitionsInProgressToDone() {
        sampleTask.setStatus(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        Optional<Task> result = taskService.completeTask(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    @DisplayName("completeTask returns empty when task is not found")
    void completeTask_returnsEmpty_whenNotFound() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(taskService.completeTask(99L)).isEmpty();
    }

    @Test
    @DisplayName("completeTask throws IllegalStateException when task is already DONE")
    void completeTask_throwsException_whenAlreadyDone() {
        sampleTask.setStatus(TaskStatus.DONE);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        assertThatThrownBy(() -> taskService.completeTask(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");
    }

    // ── reopenTask (state-transition mutation) ────────────────────────────────────

    @Test
    @DisplayName("reopenTask transitions DONE task back to TODO")
    void reopenTask_transitionsDoneToTodo() {
        sampleTask.setStatus(TaskStatus.DONE);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        Optional<Task> result = taskService.reopenTask(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    @DisplayName("reopenTask returns empty when task is not found")
    void reopenTask_returnsEmpty_whenNotFound() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(taskService.reopenTask(99L)).isEmpty();
    }

    @Test
    @DisplayName("reopenTask throws IllegalStateException when task is TODO")
    void reopenTask_throwsException_whenTodo() {
        // Only DONE tasks can be reopened
        assertThat(sampleTask.getStatus()).isEqualTo(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        assertThatThrownBy(() -> taskService.reopenTask(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TODO");
    }

    @Test
    @DisplayName("reopenTask throws IllegalStateException when task is IN_PROGRESS")
    void reopenTask_throwsException_whenInProgress() {
        sampleTask.setStatus(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        assertThatThrownBy(() -> taskService.reopenTask(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_PROGRESS");
    }
}
