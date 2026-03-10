package com.example.neo4jgraphapi.config;

import com.example.neo4jgraphapi.domain.Movie;
import com.example.neo4jgraphapi.domain.Person;
import com.example.neo4jgraphapi.repository.MovieRepository;
import com.example.neo4jgraphapi.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads sample graph data on application startup (only if the database is empty).
 *
 * <p>Implements {@link CommandLineRunner} via a {@code @Bean} method, which runs
 * after the Spring context is fully initialized. This pattern is common for
 * seeding development or demo data.</p>
 *
 * <p>Graph created:
 * <pre>
 *   (Keanu Reeves)-[:ACTED_IN]->(The Matrix)
 *   (Keanu Reeves)-[:ACTED_IN]->(John Wick)
 *   (Laurence Fishburne)-[:ACTED_IN]->(The Matrix)
 *   (Lana Wachowski)-[:DIRECTED]->(The Matrix)
 *   (Lilly Wachowski)-[:DIRECTED]->(The Matrix)
 *   (Chad Stahelski)-[:DIRECTED]->(John Wick)
 *   (Keanu Reeves)-[:FOLLOWS]->(Laurence Fishburne)
 * </pre>
 * </p>
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * Seeds the Neo4j database with sample nodes and relationships.
     * Only runs when the database has no Person nodes yet.
     *
     * @param personRepository repository for Person nodes
     * @param movieRepository  repository for Movie nodes
     * @return a CommandLineRunner that executes at startup
     */
    @Bean
    CommandLineRunner initGraph(PersonRepository personRepository, MovieRepository movieRepository) {
        return args -> {
            // Skip seeding if data already exists to avoid duplicate nodes on restart
            if (personRepository.count() > 0) {
                log.info("Graph database already contains data — skipping seed.");
                return;
            }

            log.info("Seeding graph database with sample data...");

            // ---- Create Movie nodes ----
            Movie matrix = new Movie("The Matrix", 1999, "Welcome to the Real World.");
            Movie johnWick = new Movie("John Wick", 2014, "Don't set him off.");

            // Persist Movie nodes first so they have IDs before being referenced
            matrix = movieRepository.save(matrix);
            johnWick = movieRepository.save(johnWick);

            // ---- Create Person nodes with relationships ----

            // Keanu Reeves — acted in both movies
            Person keanu = new Person("Keanu Reeves", 1964);
            keanu.getActedIn().add(matrix);
            keanu.getActedIn().add(johnWick);

            // Laurence Fishburne — acted in The Matrix
            Person laurence = new Person("Laurence Fishburne", 1961);
            laurence.getActedIn().add(matrix);

            // Lana Wachowski — directed The Matrix
            Person lana = new Person("Lana Wachowski", 1965);
            lana.getDirected().add(matrix);

            // Lilly Wachowski — directed The Matrix
            Person lilly = new Person("Lilly Wachowski", 1967);
            lilly.getDirected().add(matrix);

            // Chad Stahelski — directed John Wick
            Person chad = new Person("Chad Stahelski", 1968);
            chad.getDirected().add(johnWick);

            // Persist all Person nodes (relationships are cascade-saved automatically)
            laurence = personRepository.save(laurence);
            lana = personRepository.save(lana);
            lilly = personRepository.save(lilly);
            chad = personRepository.save(chad);

            // Save Keanu last so we can add the FOLLOWS relationship to Laurence
            keanu.getFollows().add(laurence);
            personRepository.save(keanu);

            log.info("Graph seeding complete. Created {} persons and {} movies.",
                    personRepository.count(), movieRepository.count());
        };
    }
}
