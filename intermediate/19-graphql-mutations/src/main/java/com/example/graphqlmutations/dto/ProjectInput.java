package com.example.graphqlmutations.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object (DTO) representing the input payload for creating or
 * updating a {@link com.example.graphqlmutations.domain.Project}.
 *
 * <p>In GraphQL, {@code input} types are separate from output types (regular
 * types). Input types are used exclusively as argument types in mutations.
 * This class corresponds to the {@code ProjectInput} input type defined in
 * {@code schema.graphqls}.
 *
 * <p>Spring for GraphQL automatically deserializes the GraphQL {@code input}
 * argument into this DTO when the controller method parameter is annotated
 * with {@code @Argument}.
 *
 * <p>Bean Validation annotations (from {@code jakarta.validation}) enforce
 * constraints before the DTO reaches service layer code. If validation fails,
 * Spring for GraphQL returns a structured GraphQL error to the client.
 */
public class ProjectInput {

    /**
     * Name of the project.
     * {@code @NotBlank} ensures the name is not null, empty, or whitespace-only.
     */
    @NotBlank(message = "Project name must not be blank")
    private String name;

    /**
     * Optional description for the project.
     * May be null or empty.
     */
    private String description;

    /** Default no-arg constructor required for Jackson deserialization. */
    public ProjectInput() {
    }

    /**
     * Convenience constructor used in unit tests.
     *
     * @param name        the project name
     * @param description the optional description
     */
    public ProjectInput(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
