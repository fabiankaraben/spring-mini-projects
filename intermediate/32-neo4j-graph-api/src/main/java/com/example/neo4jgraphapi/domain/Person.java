package com.example.neo4jgraphapi.domain;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.schema.Relationship.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j node representing a Person in the graph.
 *
 * <p>The {@code @Node} annotation marks this class as a graph node with the
 * label "Person". Each instance corresponds to a vertex in the Neo4j graph.</p>
 *
 * <p>Relationships defined here describe edges going OUT from this Person node:
 * <ul>
 *   <li>ACTED_IN  → Person acted in a Movie</li>
 *   <li>DIRECTED  → Person directed a Movie</li>
 *   <li>FOLLOWS   → Person follows another Person (social graph)</li>
 * </ul>
 * </p>
 */
@Node("Person")
public class Person {

    /**
     * Internal Neo4j node ID, generated automatically.
     * Using {@code @Id @GeneratedValue} lets Neo4j assign a unique Long ID.
     */
    @Id
    @GeneratedValue
    private Long id;

    /** The person's full name (used as a unique business key in queries). */
    private String name;

    /** Birth year of the person (optional, can be null). */
    private Integer born;

    /**
     * Movies this person has acted in.
     * Direction.OUTGOING means the relationship arrow points from Person → Movie.
     * The relationship type in the graph is "ACTED_IN".
     */
    @Relationship(type = "ACTED_IN", direction = Direction.OUTGOING)
    private List<Movie> actedIn = new ArrayList<>();

    /**
     * Movies this person has directed.
     * The relationship type in the graph is "DIRECTED".
     */
    @Relationship(type = "DIRECTED", direction = Direction.OUTGOING)
    private List<Movie> directed = new ArrayList<>();

    /**
     * Other people this person follows.
     * This models a directed social-graph edge: Person → FOLLOWS → Person.
     */
    @Relationship(type = "FOLLOWS", direction = Direction.OUTGOING)
    private List<Person> follows = new ArrayList<>();

    /** Required no-arg constructor for Spring Data Neo4j OGM. */
    public Person() {}

    /** Convenience constructor for creating a Person with a name and birth year. */
    public Person(String name, Integer born) {
        this.name = name;
        this.born = born;
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

    public List<Movie> getActedIn() {
        return actedIn;
    }

    public void setActedIn(List<Movie> actedIn) {
        this.actedIn = actedIn;
    }

    public List<Movie> getDirected() {
        return directed;
    }

    public void setDirected(List<Movie> directed) {
        this.directed = directed;
    }

    public List<Person> getFollows() {
        return follows;
    }

    public void setFollows(List<Person> follows) {
        this.follows = follows;
    }
}
