package com.example.graphqlmutations.service;

import com.example.graphqlmutations.domain.Project;
import com.example.graphqlmutations.dto.ProjectInput;
import com.example.graphqlmutations.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for project-related business logic.
 *
 * <p>Mediates between the GraphQL resolver
 * ({@link com.example.graphqlmutations.controller.ProjectController}) and the JPA
 * repository ({@link ProjectRepository}). All write operations are wrapped in
 * transactions to guarantee data consistency.
 *
 * <p>The service layer is intentionally kept independent of GraphQL — it works with
 * domain objects and DTOs, not GraphQL-specific types. This makes the business logic
 * easily unit-testable without starting the full Spring context.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    /**
     * Constructor injection is preferred over field injection because it makes
     * dependencies explicit and allows unit tests to pass mocks via the constructor.
     *
     * @param projectRepository repository for project persistence operations
     */
    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    // ── Read operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all projects from the database.
     *
     * <p>{@code @Transactional(readOnly = true)} hints to Hibernate that no data
     * will be modified, allowing it to skip dirty-checking and potentially use
     * a read-only JDBC connection for better performance.
     *
     * @return list of all projects (empty list if none exist)
     */
    @Transactional(readOnly = true)
    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    /**
     * Retrieve a single project by its primary key.
     *
     * @param id the project's primary key
     * @return an {@link Optional} containing the project, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<Project> findById(Long id) {
        return projectRepository.findById(id);
    }

    /**
     * Search projects by name (case-insensitive, substring match).
     *
     * @param name the name fragment to search for
     * @return list of matching projects
     */
    @Transactional(readOnly = true)
    public List<Project> searchByName(String name) {
        return projectRepository.findByNameContainingIgnoreCase(name);
    }

    // ── Write operations (mutations) ──────────────────────────────────────────────

    /**
     * Create and persist a new project.
     *
     * <p>This method corresponds to the {@code createProject} GraphQL mutation.
     * The input DTO is mapped to a domain entity, then persisted. JPA assigns
     * the generated primary key automatically via the database sequence.
     *
     * @param input the project data from the GraphQL mutation argument
     * @return the persisted project with its generated primary key populated
     */
    @Transactional
    public Project create(ProjectInput input) {
        // Map the DTO to a domain entity – keep the service layer free of GraphQL types
        Project project = new Project(input.getName(), input.getDescription());
        return projectRepository.save(project);
    }

    /**
     * Update an existing project's mutable fields.
     *
     * <p>This method corresponds to the {@code updateProject} GraphQL mutation.
     * JPA's "dirty checking" mechanism detects that the entity's fields were
     * modified within a transaction and automatically issues an SQL UPDATE when
     * the transaction commits. No explicit {@code save()} call is needed.
     *
     * @param id    the primary key of the project to update
     * @param input the new field values
     * @return an {@link Optional} with the updated project, or empty if not found
     */
    @Transactional
    public Optional<Project> update(Long id, ProjectInput input) {
        Optional<Project> existing = projectRepository.findById(id);
        if (existing.isEmpty()) {
            // Return empty to signal "not found" to the GraphQL controller,
            // which will return null for the mutation field (as declared in the schema).
            return Optional.empty();
        }

        Project project = existing.get();
        // Apply the new values – JPA dirty-checking will issue the UPDATE on commit
        project.setName(input.getName());
        project.setDescription(input.getDescription());

        return Optional.of(project);
    }

    /**
     * Delete a project by primary key.
     *
     * <p>This method corresponds to the {@code deleteProject} GraphQL mutation.
     * Because the {@code Project} entity has {@code CascadeType.ALL} on its tasks
     * collection, deleting a project will automatically cascade to and delete all
     * tasks belonging to that project.
     *
     * @param id the primary key of the project to delete
     * @return {@code true} if the project existed and was deleted; {@code false} if not found
     */
    @Transactional
    public boolean deleteById(Long id) {
        if (!projectRepository.existsById(id)) {
            return false;
        }
        projectRepository.deleteById(id);
        return true;
    }
}
