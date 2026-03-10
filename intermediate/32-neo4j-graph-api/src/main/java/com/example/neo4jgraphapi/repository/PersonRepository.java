package com.example.neo4jgraphapi.repository;

import com.example.neo4jgraphapi.domain.Person;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data Neo4j repository for {@link Person} nodes.
 *
 * <p>Extends {@link Neo4jRepository} which provides standard CRUD operations
 * (save, findById, findAll, delete, etc.) out of the box.</p>
 *
 * <p>Custom methods here use either Spring Data's derived-query naming
 * convention or explicit Cypher queries via {@code @Query}.</p>
 */
public interface PersonRepository extends Neo4jRepository<Person, Long> {

    /**
     * Finds a Person by their exact name.
     * Spring Data Neo4j derives the Cypher query from the method name:
     * {@code MATCH (p:Person {name: $name}) RETURN p}
     *
     * @param name the person's full name
     * @return an Optional containing the Person if found
     */
    Optional<Person> findByName(String name);

    /**
     * Finds all Person nodes whose birth year falls within the given range.
     * Derived query: {@code MATCH (p:Person) WHERE p.born >= $minBorn AND p.born <= $maxBorn RETURN p}
     *
     * @param minBorn minimum birth year (inclusive)
     * @param maxBorn maximum birth year (inclusive)
     * @return list of matching persons
     */
    List<Person> findByBornBetween(int minBorn, int maxBorn);

    /**
     * Custom Cypher query: finds all persons who acted in a specific movie.
     *
     * <p>Traverses the graph edge: (p:Person)-[:ACTED_IN]->(m:Movie {title: $title})
     * This demonstrates how to use the {@code @Query} annotation for manual Cypher.</p>
     *
     * @param title the movie title
     * @return list of persons who acted in that movie
     */
    @Query("MATCH (p:Person)-[:ACTED_IN]->(m:Movie {title: $title}) RETURN p")
    List<Person> findActorsByMovieTitle(String title);

    /**
     * Custom Cypher query: finds all persons who directed a specific movie.
     *
     * @param title the movie title
     * @return list of directors of that movie
     */
    @Query("MATCH (p:Person)-[:DIRECTED]->(m:Movie {title: $title}) RETURN p")
    List<Person> findDirectorsByMovieTitle(String title);

    /**
     * Custom Cypher query: finds all persons that a given person follows.
     *
     * <p>Graph traversal: (follower:Person {name: $name})-[:FOLLOWS]->(followed:Person)</p>
     *
     * @param name the name of the person doing the following
     * @return list of persons being followed
     */
    @Query("MATCH (follower:Person {name: $name})-[:FOLLOWS]->(followed:Person) RETURN followed")
    List<Person> findFollowedBy(String name);
}
