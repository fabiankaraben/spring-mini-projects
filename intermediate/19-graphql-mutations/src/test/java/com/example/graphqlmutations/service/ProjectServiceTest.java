package com.example.graphqlmutations.service;

import com.example.graphqlmutations.domain.Project;
import com.example.graphqlmutations.dto.ProjectInput;
import com.example.graphqlmutations.repository.ProjectRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProjectService}.
 *
 * <p>All repository dependencies are mocked with Mockito, so these tests run
 * without a database or Spring application context. This makes them fast and
 * deterministic — ideal for CI pipelines and tight feedback loops.
 *
 * <p>{@link ExtendWith} with {@code MockitoExtension} sets up the Mockito framework
 * for the test class, enabling {@code @Mock} and {@code @InjectMocks} annotations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService unit tests")
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    /**
     * Mockito automatically injects the {@code projectRepository} mock into
     * {@link ProjectService} via its constructor.
     */
    @InjectMocks
    private ProjectService projectService;

    private Project sampleProject;

    @BeforeEach
    void setUp() {
        sampleProject = new Project("Backend Refactor", "Refactor all legacy services");
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns the list from the repository")
    void findAll_returnsProjects() {
        when(projectRepository.findAll()).thenReturn(List.of(sampleProject));

        List<Project> result = projectService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Backend Refactor");
        verify(projectRepository).findAll();
    }

    @Test
    @DisplayName("findAll returns empty list when no projects exist")
    void findAll_returnsEmptyList_whenNoProjects() {
        when(projectRepository.findAll()).thenReturn(List.of());

        List<Project> result = projectService.findAll();

        assertThat(result).isEmpty();
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns Optional with project when found")
    void findById_returnsProject_whenFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(sampleProject));

        Optional<Project> result = projectService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Backend Refactor");
    }

    @Test
    @DisplayName("findById returns empty Optional when not found")
    void findById_returnsEmpty_whenNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(projectService.findById(99L)).isEmpty();
    }

    // ── searchByName ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("searchByName delegates to repository with the correct name")
    void searchByName_delegatesToRepository() {
        when(projectRepository.findByNameContainingIgnoreCase("Refactor"))
                .thenReturn(List.of(sampleProject));

        List<Project> result = projectService.searchByName("Refactor");

        assertThat(result).hasSize(1);
        verify(projectRepository).findByNameContainingIgnoreCase("Refactor");
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create builds Project from input and calls save")
    void create_savesNewProject() {
        when(projectRepository.save(any(Project.class))).thenReturn(sampleProject);

        ProjectInput input = new ProjectInput("Backend Refactor", "Refactor all legacy services");
        Project result = projectService.create(input);

        assertThat(result.getName()).isEqualTo("Backend Refactor");
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    @DisplayName("create allows null description in input")
    void create_allowsNullDescription() {
        Project projectNoDesc = new Project("Quick Project", null);
        when(projectRepository.save(any(Project.class))).thenReturn(projectNoDesc);

        ProjectInput input = new ProjectInput("Quick Project", null);
        Project result = projectService.create(input);

        assertThat(result.getDescription()).isNull();
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update returns empty when project is not found")
    void update_returnsEmpty_whenNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Project> result = projectService.update(99L,
                new ProjectInput("New Name", null));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("update modifies the project fields when found")
    void update_modifiesFields_whenFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(sampleProject));

        ProjectInput input = new ProjectInput("New Name", "New Description");
        Optional<Project> result = projectService.update(1L, input);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("New Name");
        assertThat(result.get().getDescription()).isEqualTo("New Description");
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById returns false when project does not exist")
    void deleteById_returnsFalse_whenNotFound() {
        when(projectRepository.existsById(99L)).thenReturn(false);

        assertThat(projectService.deleteById(99L)).isFalse();
        verify(projectRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteById returns true and calls repository.deleteById when found")
    void deleteById_returnsTrue_andDeletes_whenFound() {
        when(projectRepository.existsById(1L)).thenReturn(true);

        assertThat(projectService.deleteById(1L)).isTrue();
        verify(projectRepository).deleteById(1L);
    }
}
