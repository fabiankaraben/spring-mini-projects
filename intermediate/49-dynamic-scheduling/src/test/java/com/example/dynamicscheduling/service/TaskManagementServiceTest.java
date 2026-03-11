package com.example.dynamicscheduling.service;

import com.example.dynamicscheduling.dto.CreateTaskRequest;
import com.example.dynamicscheduling.dto.TaskStatusResponse;
import com.example.dynamicscheduling.dto.UpdateIntervalRequest;
import com.example.dynamicscheduling.model.TaskConfig;
import com.example.dynamicscheduling.repository.TaskConfigRepository;
import com.example.dynamicscheduling.scheduling.DynamicSchedulingConfigurer;
import com.example.dynamicscheduling.scheduling.DynamicTaskRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TaskManagementService}.
 *
 * <p>All collaborators ({@link TaskConfigRepository}, {@link DynamicTaskRegistry},
 * {@link DynamicSchedulingConfigurer}) are replaced with Mockito mocks, so
 * these tests run purely in memory without a Spring context or database.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>createTask – happy path and duplicate-name rejection.</li>
 *   <li>updateInterval – happy path and not-found rejection.</li>
 *   <li>enableTask / disableTask – state-transition happy paths and error paths.</li>
 *   <li>deleteTask – happy path and not-found rejection.</li>
 *   <li>listTasks / getTask – delegation to repository.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskManagementService – unit tests")
class TaskManagementServiceTest {

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @Mock
    private DynamicTaskRegistry registry;

    @Mock
    private DynamicSchedulingConfigurer configurer;

    /** Service under test – wired with mock collaborators. */
    private TaskManagementService service;

    @BeforeEach
    void setUp() {
        service = new TaskManagementService(taskConfigRepository, registry, configurer);
    }

    // ── createTask ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createTask – persists config and registers with scheduler")
    void createTask_validRequest_persistsAndRegisters() {
        // Given
        CreateTaskRequest request = new CreateTaskRequest("my-task", "desc", 5000L, true);
        TaskConfig savedConfig = new TaskConfig("my-task", "desc", 5000L, true);

        when(taskConfigRepository.existsByTaskName("my-task")).thenReturn(false);
        when(taskConfigRepository.save(any())).thenReturn(savedConfig);
        when(registry.getInterval("my-task", 5000L)).thenReturn(5000L);

        // When
        TaskStatusResponse response = service.createTask(request);

        // Then: task was saved and registered with the scheduler
        verify(taskConfigRepository).save(any(TaskConfig.class));
        verify(configurer).registerTask(savedConfig);

        assertThat(response.taskName()).isEqualTo("my-task");
        assertThat(response.configuredIntervalMs()).isEqualTo(5000L);
        assertThat(response.enabled()).isTrue();
    }

    @Test
    @DisplayName("createTask – throws IllegalArgumentException when task name already exists")
    void createTask_duplicateName_throwsIllegalArgumentException() {
        // Given: a task with that name already exists
        when(taskConfigRepository.existsByTaskName("dup")).thenReturn(true);

        CreateTaskRequest request = new CreateTaskRequest("dup", null, 1000L, true);

        // When / Then: service must throw with a clear message
        assertThatThrownBy(() -> service.createTask(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup");
    }

    // ── updateInterval ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateInterval – updates DB and live registry")
    void updateInterval_existingTask_updatesDbAndRegistry() {
        // Given
        TaskConfig config = new TaskConfig("hb", "heartbeat", 3000L, true);
        when(taskConfigRepository.findByTaskName("hb")).thenReturn(Optional.of(config));
        when(taskConfigRepository.save(any())).thenReturn(config);
        when(registry.getInterval("hb", 10000L)).thenReturn(10000L);

        UpdateIntervalRequest request = new UpdateIntervalRequest(10000L);

        // When
        TaskStatusResponse response = service.updateInterval("hb", request);

        // Then: the registry is updated immediately
        verify(registry).setInterval("hb", 10000L);
        assertThat(response.taskName()).isEqualTo("hb");
    }

    @Test
    @DisplayName("updateInterval – throws IllegalArgumentException when task not found")
    void updateInterval_unknownTask_throwsNotFound() {
        when(taskConfigRepository.findByTaskName("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateInterval("missing", new UpdateIntervalRequest(2000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ── enableTask ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("enableTask – updates DB, registry, and schedules future")
    void enableTask_disabledTask_enablesAndSchedules() {
        // Given: a disabled task
        TaskConfig config = new TaskConfig("sync", "data sync", 15000L, false);
        when(taskConfigRepository.findByTaskName("sync")).thenReturn(Optional.of(config));
        when(taskConfigRepository.save(any())).thenReturn(config);
        when(registry.getInterval("sync", 15000L)).thenReturn(15000L);

        // When
        service.enableTask("sync");

        // Then: registry enabled flag updated and task scheduled
        verify(registry).setEnabled("sync", true);
        verify(configurer).scheduleTask("sync");
    }

    @Test
    @DisplayName("enableTask – throws IllegalArgumentException when task is already enabled")
    void enableTask_alreadyEnabled_throwsIllegalArgumentException() {
        // Given: already-enabled task
        TaskConfig config = new TaskConfig("report", "reports", 30000L, true);
        when(taskConfigRepository.findByTaskName("report")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.enableTask("report"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already enabled");
    }

    // ── disableTask ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disableTask – cancels future and updates DB")
    void disableTask_enabledTask_cancelsAndUpdatesDb() {
        // Given: an enabled task
        TaskConfig config = new TaskConfig("heartbeat", "ping", 3000L, true);
        when(taskConfigRepository.findByTaskName("heartbeat")).thenReturn(Optional.of(config));
        when(taskConfigRepository.save(any())).thenReturn(config);
        when(registry.getInterval("heartbeat", 3000L)).thenReturn(3000L);

        // When
        service.disableTask("heartbeat");

        // Then: registry flag set to false and future cancelled
        verify(registry).setEnabled("heartbeat", false);
        verify(registry).cancelFuture("heartbeat");
    }

    @Test
    @DisplayName("disableTask – throws IllegalArgumentException when task is already disabled")
    void disableTask_alreadyDisabled_throwsIllegalArgumentException() {
        TaskConfig config = new TaskConfig("sync", "sync", 15000L, false);
        when(taskConfigRepository.findByTaskName("sync")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.disableTask("sync"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already disabled");
    }

    // ── deleteTask ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteTask – removes from registry and deletes from DB")
    void deleteTask_existingTask_removesAndDeletes() {
        TaskConfig config = new TaskConfig("cleanup", "cleanup job", 60000L, true);
        when(taskConfigRepository.findByTaskName("cleanup")).thenReturn(Optional.of(config));

        service.deleteTask("cleanup");

        verify(registry).remove("cleanup");
        verify(taskConfigRepository).delete(config);
    }

    @Test
    @DisplayName("deleteTask – throws IllegalArgumentException when task not found")
    void deleteTask_unknownTask_throwsNotFound() {
        when(taskConfigRepository.findByTaskName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTask("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ── listTasks / getTask ───────────────────────────────────────────────────────

    @Test
    @DisplayName("listTasks – delegates to repository and maps to DTOs")
    void listTasks_returnsMappedDtos() {
        TaskConfig c1 = new TaskConfig("heartbeat", "ping", 3000L, true);
        TaskConfig c2 = new TaskConfig("report", "reports", 30000L, true);
        when(taskConfigRepository.findAll()).thenReturn(List.of(c1, c2));
        when(registry.getInterval("heartbeat", 3000L)).thenReturn(3000L);
        when(registry.getInterval("report", 30000L)).thenReturn(30000L);

        List<TaskStatusResponse> result = service.listTasks();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TaskStatusResponse::taskName)
                          .containsExactlyInAnyOrder("heartbeat", "report");
    }

    @Test
    @DisplayName("getTask – returns DTO for existing task")
    void getTask_existingTask_returnsDto() {
        TaskConfig config = new TaskConfig("heartbeat", "ping", 3000L, true);
        when(taskConfigRepository.findByTaskName("heartbeat")).thenReturn(Optional.of(config));
        when(registry.getInterval("heartbeat", 3000L)).thenReturn(3000L);

        TaskStatusResponse response = service.getTask("heartbeat");

        assertThat(response.taskName()).isEqualTo("heartbeat");
        assertThat(response.configuredIntervalMs()).isEqualTo(3000L);
        assertThat(response.enabled()).isTrue();
    }

    @Test
    @DisplayName("getTask – throws IllegalArgumentException when task not found")
    void getTask_unknownTask_throwsNotFound() {
        when(taskConfigRepository.findByTaskName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTask("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
