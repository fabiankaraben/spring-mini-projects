package com.example.graphqlmutations.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Project} domain entity.
 *
 * <p>These tests verify the entity's constructor, getters, and setters in
 * isolation — no Spring context, no database, no GraphQL layer. This is the
 * fastest possible level of testing and gives immediate feedback during
 * development.
 *
 * <p>{@code @ExtendWith(MockitoExtension.class)} is not needed here because
 * there are no mocked collaborators. Plain JUnit 5 is sufficient.
 */
@DisplayName("Project domain entity unit tests")
class ProjectTest {

    @Test
    @DisplayName("Constructor sets name and description correctly")
    void constructor_setsFields() {
        Project project = new Project("Backend Refactor", "Refactor all legacy services");

        assertThat(project.getName()).isEqualTo("Backend Refactor");
        assertThat(project.getDescription()).isEqualTo("Refactor all legacy services");
    }

    @Test
    @DisplayName("Constructor allows null description")
    void constructor_allowsNullDescription() {
        Project project = new Project("Quick Fix", null);

        assertThat(project.getName()).isEqualTo("Quick Fix");
        assertThat(project.getDescription()).isNull();
    }

    @Test
    @DisplayName("getId returns null before persistence (no ID assigned yet)")
    void getId_returnsNull_beforePersistence() {
        // Before saving to the database, the auto-generated ID is null
        Project project = new Project("New Project", null);
        assertThat(project.getId()).isNull();
    }

    @Test
    @DisplayName("getTasks returns empty list on new project")
    void getTasks_returnsEmptyList_initially() {
        Project project = new Project("Empty Project", null);
        // A new project starts with no tasks
        assertThat(project.getTasks()).isNotNull();
        assertThat(project.getTasks()).isEmpty();
    }

    @Test
    @DisplayName("setName updates the project name")
    void setName_updatesName() {
        Project project = new Project("Old Name", null);
        project.setName("New Name");
        assertThat(project.getName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("setDescription updates the project description")
    void setDescription_updatesDescription() {
        Project project = new Project("Project", "Original description");
        project.setDescription("Updated description");
        assertThat(project.getDescription()).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("setDescription allows setting description to null")
    void setDescription_allowsNull() {
        Project project = new Project("Project", "Some description");
        project.setDescription(null);
        assertThat(project.getDescription()).isNull();
    }
}
