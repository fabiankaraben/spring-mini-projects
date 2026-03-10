package com.example.graphqlapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Input DTO used for creating or updating an {@link com.example.graphqlapi.domain.Author}.
 *
 * <p>In GraphQL, <em>input types</em> are separate types from output types.
 * This class mirrors the {@code AuthorInput} input type defined in the GraphQL
 * schema ({@code schema.graphqls}). Spring for GraphQL automatically binds the
 * fields of the incoming GraphQL input object to this Java record/class.
 *
 * <p>Using a dedicated DTO (rather than the JPA entity directly) keeps the
 * GraphQL API contract decoupled from the persistence model. Validation annotations
 * here give us bean-validation checks before the data reaches the service layer.
 */
public class AuthorInput {

    /**
     * The author's full name. Required – cannot be blank.
     *
     * <p>{@code @NotBlank} rejects {@code null}, empty strings, and whitespace-only strings.
     */
    @NotBlank(message = "Author name must not be blank")
    private String name;

    /**
     * Short biography of the author. Optional – may be {@code null} or blank.
     */
    private String bio;

    /** No-arg constructor required for Spring's data-binding mechanism. */
    public AuthorInput() {
    }

    public AuthorInput(String name, String bio) {
        this.name = name;
        this.bio = bio;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
