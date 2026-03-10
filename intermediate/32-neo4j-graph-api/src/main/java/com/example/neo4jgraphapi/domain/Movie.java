package com.example.neo4jgraphapi.domain;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Neo4j node representing a Movie in the graph.
 *
 * <p>The {@code @Node} annotation marks this class as a graph node with the
 * label "Movie". Each instance corresponds to a vertex in the Neo4j graph.</p>
 *
 * <p>Relationships TO this node (incoming) are declared on the {@link Person}
 * class (ACTED_IN, DIRECTED). This keeps the ownership of relationships
 * consistent: a Person acts in / directs a Movie.</p>
 */
@Node("Movie")
public class Movie {

    /**
     * Internal Neo4j node ID, generated automatically by the database.
     */
    @Id
    @GeneratedValue
    private Long id;

    /** The movie title (used as a unique business key in queries). */
    private String title;

    /** Year the movie was released (optional). */
    private Integer released;

    /** Short tagline or description for the movie (optional). */
    private String tagline;

    /** Required no-arg constructor for Spring Data Neo4j OGM. */
    public Movie() {}

    /** Convenience constructor for creating a Movie with title and release year. */
    public Movie(String title, Integer released) {
        this.title = title;
        this.released = released;
    }

    /** Full constructor including tagline. */
    public Movie(String title, Integer released, String tagline) {
        this.title = title;
        this.released = released;
        this.tagline = tagline;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
