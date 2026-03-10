package com.example.neo4jgraphapi.service;

import com.example.neo4jgraphapi.domain.Movie;
import com.example.neo4jgraphapi.domain.Person;
import com.example.neo4jgraphapi.dto.CreatePersonRequest;
import com.example.neo4jgraphapi.repository.MovieRepository;
import com.example.neo4jgraphapi.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Person-related operations.
 *
 * <p>This class encapsulates business logic and graph operations for Person nodes.
 * It delegates to {@link PersonRepository} and {@link MovieRepository} for
 * persistence, and uses Spring's {@code @Transactional} to ensure graph
 * consistency when creating nodes and relationships.</p>
 */
@Service
public class PersonService {

    /** Repository for Person node CRUD and custom Cypher queries. */
    private final PersonRepository personRepository;

    /** Repository for Movie nodes, needed when creating relationships. */
    private final MovieRepository movieRepository;

    /** Constructor injection (preferred over field injection for testability). */
    public PersonService(PersonRepository personRepository, MovieRepository movieRepository) {
        this.personRepository = personRepository;
        this.movieRepository = movieRepository;
    }

    /**
     * Creates a new Person node in the graph.
     *
     * @param request DTO containing name and optional birth year
     * @return the saved Person node (with its generated Neo4j ID populated)
     */
    @Transactional
    public Person createPerson(CreatePersonRequest request) {
        // Map DTO → domain entity
        Person person = new Person(request.getName(), request.getBorn());
        // Persist the node — Spring Data Neo4j issues a CREATE or MERGE Cypher statement
        return personRepository.save(person);
    }

    /**
     * Returns all Person nodes in the graph.
     *
     * @return list of all persons
     */
    @Transactional(readOnly = true)
    public List<Person> findAll() {
        return personRepository.findAll();
    }

    /**
     * Finds a Person by their internal Neo4j node ID.
     *
     * @param id the node's generated ID
     * @return Optional Person
     */
    @Transactional(readOnly = true)
    public Optional<Person> findById(Long id) {
        return personRepository.findById(id);
    }

    /**
     * Finds a Person by their name.
     *
     * @param name the person's full name
     * @return Optional Person
     */
    @Transactional(readOnly = true)
    public Optional<Person> findByName(String name) {
        return personRepository.findByName(name);
    }

    /**
     * Finds all persons born within a given year range.
     *
     * @param minBorn minimum birth year (inclusive)
     * @param maxBorn maximum birth year (inclusive)
     * @return list of matching persons
     */
    @Transactional(readOnly = true)
    public List<Person> findByBornBetween(int minBorn, int maxBorn) {
        return personRepository.findByBornBetween(minBorn, maxBorn);
    }

    /**
     * Deletes a Person node by ID.
     * Note: Spring Data Neo4j also removes all relationships attached to this node.
     *
     * @param id the node's generated ID
     */
    @Transactional
    public void deletePerson(Long id) {
        personRepository.deleteById(id);
    }

    /**
     * Creates an ACTED_IN relationship between a Person and a Movie.
     *
     * <p>This method demonstrates creating a graph relationship (edge) between
     * two existing nodes. The relationship is stored by updating the Person
     * entity's {@code actedIn} list and re-saving.</p>
     *
     * @param personName the actor's name
     * @param movieTitle the movie title
     * @return the updated Person node with the new relationship
     * @throws IllegalArgumentException if either the person or movie does not exist
     */
    @Transactional
    public Person addActedIn(String personName, String movieTitle) {
        // Fetch both nodes — they must already exist in the graph
        Person person = personRepository.findByName(personName)
                .orElseThrow(() -> new IllegalArgumentException("Person not found: " + personName));
        Movie movie = movieRepository.findByTitle(movieTitle)
                .orElseThrow(() -> new IllegalArgumentException("Movie not found: " + movieTitle));

        // Add the movie to the person's actedIn list — this creates the ACTED_IN edge
        if (!person.getActedIn().contains(movie)) {
            person.getActedIn().add(movie);
        }
        // Saving the Person node persists the new relationship in Neo4j
        return personRepository.save(person);
    }

    /**
     * Creates a DIRECTED relationship between a Person and a Movie.
     *
     * @param personName the director's name
     * @param movieTitle the movie title
     * @return the updated Person node
     * @throws IllegalArgumentException if either node does not exist
     */
    @Transactional
    public Person addDirected(String personName, String movieTitle) {
        Person person = personRepository.findByName(personName)
                .orElseThrow(() -> new IllegalArgumentException("Person not found: " + personName));
        Movie movie = movieRepository.findByTitle(movieTitle)
                .orElseThrow(() -> new IllegalArgumentException("Movie not found: " + movieTitle));

        if (!person.getDirected().contains(movie)) {
            person.getDirected().add(movie);
        }
        return personRepository.save(person);
    }

    /**
     * Creates a FOLLOWS relationship between two Person nodes.
     *
     * <p>This models a directed social-graph edge:
     * (followerName:Person)-[:FOLLOWS]->(followedName:Person)</p>
     *
     * @param followerName the person who is following
     * @param followedName the person being followed
     * @return the updated follower Person node
     * @throws IllegalArgumentException if either person does not exist
     */
    @Transactional
    public Person addFollows(String followerName, String followedName) {
        Person follower = personRepository.findByName(followerName)
                .orElseThrow(() -> new IllegalArgumentException("Person not found: " + followerName));
        Person followed = personRepository.findByName(followedName)
                .orElseThrow(() -> new IllegalArgumentException("Person not found: " + followedName));

        if (!follower.getFollows().contains(followed)) {
            follower.getFollows().add(followed);
        }
        return personRepository.save(follower);
    }

    /**
     * Returns all persons who acted in a given movie (graph traversal query).
     *
     * @param movieTitle the movie title
     * @return list of actors
     */
    @Transactional(readOnly = true)
    public List<Person> findActorsByMovie(String movieTitle) {
        return personRepository.findActorsByMovieTitle(movieTitle);
    }

    /**
     * Returns all persons who directed a given movie (graph traversal query).
     *
     * @param movieTitle the movie title
     * @return list of directors
     */
    @Transactional(readOnly = true)
    public List<Person> findDirectorsByMovie(String movieTitle) {
        return personRepository.findDirectorsByMovieTitle(movieTitle);
    }

    /**
     * Returns all persons followed by the given person.
     *
     * @param personName the follower's name
     * @return list of followed persons
     */
    @Transactional(readOnly = true)
    public List<Person> findFollowedBy(String personName) {
        return personRepository.findFollowedBy(personName);
    }
}
