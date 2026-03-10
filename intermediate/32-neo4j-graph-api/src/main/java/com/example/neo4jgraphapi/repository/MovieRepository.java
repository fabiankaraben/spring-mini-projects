package com.example.neo4jgraphapi.repository;

import com.example.neo4jgraphapi.domain.Movie;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data Neo4j repository for {@link Movie} nodes.
 *
 * <p>Extends {@link Neo4jRepository} to get CRUD operations for free.
 * Custom graph queries are expressed in Cypher using the {@code @Query} annotation.</p>
 */
public interface MovieRepository extends Neo4jRepository<Movie, Long> {

    /**
     * Finds a Movie by its exact title.
     * Derived query: {@code MATCH (m:Movie {title: $title}) RETURN m}
     *
     * @param title the movie title
     * @return an Optional containing the Movie if found
     */
    Optional<Movie> findByTitle(String title);

    /**
     * Finds all movies released in a specific year.
     * Derived query: {@code MATCH (m:Movie {released: $released}) RETURN m}
     *
     * @param released the release year
     * @return list of movies released in that year
     */
    List<Movie> findByReleased(int released);

    /**
     * Finds all movies released within a year range.
     * Derived query: {@code MATCH (m:Movie) WHERE m.released >= $minYear AND m.released <= $maxYear RETURN m}
     *
     * @param minYear minimum release year (inclusive)
     * @param maxYear maximum release year (inclusive)
     * @return list of matching movies
     */
    List<Movie> findByReleasedBetween(int minYear, int maxYear);

    /**
     * Custom Cypher query: finds all movies a given person acted in.
     *
     * <p>Graph traversal: (p:Person {name: $name})-[:ACTED_IN]->(m:Movie)</p>
     *
     * @param personName the actor's name
     * @return list of movies the actor appeared in
     */
    @Query("MATCH (p:Person {name: $personName})-[:ACTED_IN]->(m:Movie) RETURN m")
    List<Movie> findMoviesActedInByPerson(String personName);

    /**
     * Custom Cypher query: finds all movies directed by a given person.
     *
     * <p>Graph traversal: (p:Person {name: $name})-[:DIRECTED]->(m:Movie)</p>
     *
     * @param personName the director's name
     * @return list of movies directed by that person
     */
    @Query("MATCH (p:Person {name: $personName})-[:DIRECTED]->(m:Movie) RETURN m")
    List<Movie> findMoviesDirectedByPerson(String personName);

    /**
     * Custom Cypher query: recommends movies that co-actors of a given person have acted in,
     * but which the person has not yet acted in themselves.
     *
     * <p>This is a two-hop graph traversal:
     * (person)-[:ACTED_IN]->(shared movie)<-[:ACTED_IN]-(co-actor)-[:ACTED_IN]->(recommended movie)
     * </p>
     *
     * @param personName the person to generate recommendations for
     * @return list of recommended movies (distinct, not already acted in by the person)
     */
    @Query("""
            MATCH (me:Person {name: $personName})-[:ACTED_IN]->(:Movie)<-[:ACTED_IN]-(coActor:Person),
                  (coActor)-[:ACTED_IN]->(recommended:Movie)
            WHERE NOT (me)-[:ACTED_IN]->(recommended)
            RETURN DISTINCT recommended
            """)
    List<Movie> findRecommendationsForPerson(String personName);
}
