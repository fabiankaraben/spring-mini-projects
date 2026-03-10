package com.example.neo4jgraphapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new Movie node.
 */
public class CreateMovieRequest {

    /** The movie title. Cannot be blank. */
    @NotBlank(message = "Title is required")
    private String title;

    /** Year the movie was released (optional). */
    private Integer released;

    /** Short tagline or description (optional). */
    private String tagline;

    public CreateMovieRequest() {}

    public CreateMovieRequest(String title, Integer released, String tagline) {
        this.title = title;
        this.released = released;
        this.tagline = tagline;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getReleased() {
        return released;
    }

    public void setReleased(Integer released) {
        this.released = released;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }
}
