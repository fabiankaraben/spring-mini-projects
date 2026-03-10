package com.example.neo4jgraphapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Movie} domain entity.
 *
 * <p>Pure unit tests — no Spring context, no database.
 * Verifies constructors, getters, setters, and default field values.</p>
 */
@DisplayName("Movie domain entity tests")
class MovieTest {

    @Test
    @DisplayName("Two-arg constructor should set title and released")
    void twoArgConstructor_setsTitleAndReleased() {
        Movie movie = new Movie("The Matrix", 1999);

        assertThat(movie.getTitle()).isEqualTo("The Matrix");
        assertThat(movie.getReleased()).isEqualTo(1999);
        assertThat(movie.getTagline()).isNull();
    }

    @Test
    @DisplayName("Three-arg constructor should set title, released, and tagline")
    void threeArgConstructor_setsTitleReleasedAndTagline() {
        Movie movie = new Movie("The Matrix", 1999, "Welcome to the Real World.");

        assertThat(movie.getTitle()).isEqualTo("The Matrix");
        assertThat(movie.getReleased()).isEqualTo(1999);
        assertThat(movie.getTagline()).isEqualTo("Welcome to the Real World.");
    }

    @Test
    @DisplayName("No-arg constructor should create movie with null fields")
    void noArgConstructor_createsMovieWithNullFields() {
        // Required by Spring Data Neo4j OGM for reflective instantiation
        Movie movie = new Movie();

        assertThat(movie.getTitle()).isNull();
        assertThat(movie.getReleased()).isNull();
        assertThat(movie.getTagline()).isNull();
        assertThat(movie.getId()).isNull();
    }

    @Test
    @DisplayName("Setters should update all fields correctly")
    void setters_updateFieldsCorrectly() {
        Movie movie = new Movie();

        movie.setTitle("John Wick");
        movie.setReleased(2014);
        movie.setTagline("Don't set him off.");
        movie.setId(7L);

        assertThat(movie.getTitle()).isEqualTo("John Wick");
        assertThat(movie.getReleased()).isEqualTo(2014);
        assertThat(movie.getTagline()).isEqualTo("Don't set him off.");
        assertThat(movie.getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Released year can be null for movies without a known year")
    void releasedYear_canBeNull() {
        // Optional field — movies may not always have a release year stored
        Movie movie = new Movie("Unknown Release", null);

        assertThat(movie.getReleased()).isNull();
    }
}
