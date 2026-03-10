package com.example.graphqlmutations.controller;

import com.example.graphqlmutations.domain.Project;
import com.example.graphqlmutations.dto.ProjectInput;
import com.example.graphqlmutations.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL controller (resolver) for {@link Project}-related queries and mutations.
 *
 * <p>Spring for GraphQL scans for {@code @Controller} beans and wires their annotated
 * methods as field resolvers for the GraphQL schema. Each method maps to a field on
 * the root {@code Query} or {@code Mutation} type, with the method name used as the
 * default field name (unless overridden via annotation attributes).
 *
 * <p>The controller follows the thin-controller pattern: it only handles argument
 * binding and delegates all business logic to {@link ProjectService}.
 *
 * <p>Key annotations used here:
 * <ul>
 *   <li>{@code @QueryMapping} – maps a method to a field on the root {@code Query} type.</li>
 *   <li>{@code @MutationMapping} – maps a method to a field on the root {@code Mutation} type.</li>
 *   <li>{@code @Argument} – binds a GraphQL argument (from the query/mutation) to a Java parameter.</li>
 *   <li>{@code @Valid} – triggers Bean Validation on the bound DTO before the method body executes.</li>
 * </ul>
 */
@Controller
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // ── Query handlers ────────────────────────────────────────────────────────────

    /**
     * Resolves the {@code projects} query – returns all projects.
     *
     * <p>GraphQL schema:
     * <pre>{@code
     * type Query {
     *   projects: [Project!]!
     * }
     * }</pre>
     *
     * @return list of all projects
     */
    @QueryMapping
    public List<Project> projects() {
        return projectService.findAll();
    }

    /**
     * Resolves the {@code project(id: ID!)} query – returns one project by ID.
     *
     * @param id the project's primary key
     * @return the project, or {@code null} if not found
     */
    @QueryMapping
    public Project project(@Argument Long id) {
        return projectService.findById(id).orElse(null);
    }

    /**
     * Resolves the {@code searchProjects(name: String!)} query.
     *
     * @param name the name fragment to search for
     * @return list of matching projects
     */
    @QueryMapping
    public List<Project> searchProjects(@Argument String name) {
        return projectService.searchByName(name);
    }

    // ── Mutation handlers ─────────────────────────────────────────────────────────

    /**
     * Resolves the {@code createProject(input: ProjectInput!)} mutation.
     *
     * <p>{@code @Valid} triggers Bean Validation on the {@link ProjectInput} DTO.
     * If {@code name} is blank, validation fails and a GraphQL error is returned
     * before the method body executes.
     *
     * @param input the deserialized GraphQL {@code ProjectInput} argument
     * @return the newly created project with its generated ID
     */
    @MutationMapping
    public Project createProject(@Argument @Valid ProjectInput input) {
        return projectService.create(input);
    }

    /**
     * Resolves the {@code updateProject(id: ID!, input: ProjectInput!)} mutation.
     *
     * @param id    the ID of the project to update
     * @param input the new field values
     * @return the updated project, or {@code null} if not found
     */
    @MutationMapping
    public Project updateProject(@Argument Long id, @Argument @Valid ProjectInput input) {
        return projectService.update(id, input).orElse(null);
    }

    /**
     * Resolves the {@code deleteProject(id: ID!)} mutation.
     *
     * <p>Deleting a project also cascades to all its tasks (via JPA
     * {@code CascadeType.ALL} on the {@code tasks} collection in {@link Project}).
     *
     * @param id the ID of the project to delete
     * @return {@code true} if deleted; {@code false} if not found
     */
    @MutationMapping
    public boolean deleteProject(@Argument Long id) {
        return projectService.deleteById(id);
    }
}
