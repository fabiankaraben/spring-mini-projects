package com.example.graphqlmutations.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Task} domain entity.
 *
 * <p>These tests verify the entity's constructor defaults, getters, setters,
 * and the status lifecycle in isolation without any Spring or database infrastructure.
 */
@DisplayName("Task domain entity unit tests")
class TaskTest {

    private Project project;

    @BeforeEach
    void setUp() {
        // Create a simple project to use as the parent for tasks in each test
        project = new Project("Test Project", null);
    }

    @Test
    @DisplayName("Constructor sets title, description, priority, and project correctly")
    void constructor_setsFields() {
        Task task = new Task("Fix login bug", "Users can't log in on mobile", 1, project);

        assertThat(task.getTitle()).isEqualTo("Fix login bug");
        assertThat(task.getDescription()).isEqualTo("Users can't log in on mobile");
        assertThat(task.getPriority()).isEqualTo(1);
        assertThat(task.getProject()).isSameAs(project);
    }

    @Test
    @DisplayName("New task always starts with TODO status")
    void constructor_setsStatusToTodo() {
        // This is a critical business rule: tasks must start in TODO status
        Task task = new Task("New Task", null, 3, project);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    @DisplayName("Constructor allows null description")
    void constructor_allowsNullDescription() {
        Task task = new Task("Quick task", null, 3, project);

        assertThat(task.getDescription()).isNull();
    }

    @Test
    @DisplayName("getId returns null before persistence")
    void getId_returnsNull_beforePersistence() {
        Task task = new Task("Task", null, 3, project);
        assertThat(task.getId()).isNull();
    }

    @Test
    @DisplayName("setTitle updates the task title")
    void setTitle_updatesTitle() {
        Task task = new Task("Old Title", null, 3, project);
        task.setTitle("New Title");
        assertThat(task.getTitle()).isEqualTo("New Title");
    }

    @Test
    @DisplayName("setDescription updates the task description")
    void setDescription_updatesDescription() {
        Task task = new Task("Task", "Original description", 3, project);
        task.setDescription("Updated description");
        assertThat(task.getDescription()).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("setStatus transitions from TODO to IN_PROGRESS")
    void setStatus_transitionsToInProgress() {
        Task task = new Task("Task", null, 3, project);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);

        task.setStatus(TaskStatus.IN_PROGRESS);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("setStatus transitions from IN_PROGRESS to DONE")
    void setStatus_transitionsToDone() {
        Task task = new Task("Task", null, 3, project);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStatus(TaskStatus.DONE);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    @DisplayName("setStatus transitions from DONE back to TODO (reopen)")
    void setStatus_transitionsBackToTodo() {
        Task task = new Task("Task", null, 3, project);
        task.setStatus(TaskStatus.DONE);
        task.setStatus(TaskStatus.TODO);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    @DisplayName("setPriority updates the priority")
    void setPriority_updatesPriority() {
        Task task = new Task("Task", null, 3, project);
        task.setPriority(1);
        assertThat(task.getPriority()).isEqualTo(1);
    }

    @Test
    @DisplayName("setProject updates the owning project")
    void setProject_updatesProject() {
        Task task = new Task("Task", null, 3, project);
        Project newProject = new Project("Another Project", null);
        task.setProject(newProject);
        assertThat(task.getProject()).isSameAs(newProject);
    }
}
