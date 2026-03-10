package com.example.neo4jgraphapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new Person node.
 *
 * <p>DTOs (Data Transfer Objects) decouple the API contract from the internal
 * domain model, providing a clean boundary for validation and documentation.</p>
 */
public class CreatePersonRequest {

    /** The person's full name. Cannot be blank. */
    @NotBlank(message = "Name is required")
    private String name;

    /** Birth year of the person (optional). */
    private Integer born;

    public CreatePersonRequest() {}

    public CreatePersonRequest(String name, Integer born) {
        this.name = name;
        this.born = born;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getBorn() {
        return born;
    }

    public void setBorn(Integer born) {
        this.born = born;
    }
}
