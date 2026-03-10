package com.example.neo4jgraphapi.controller;

import com.example.neo4jgraphapi.domain.Person;
import com.example.neo4jgraphapi.dto.CreatePersonRequest;
import com.example.neo4jgraphapi.service.PersonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing endpoints for Person node operations.
 *
 * <p>Base path: {@code /api/persons}</p>
 *
 * <p>Demonstrates how to expose graph CRUD and graph traversal queries
 * (relationship creation, neighbor lookup) via a standard REST API.</p>
 */
@RestController
@RequestMapping("/api/persons")
public class PersonController {

    /** Service handling all Person-related business logic. */
    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    /**
     * Creates a new Person node.
     *
     * POST /api/persons
     * Body: { "name": "Tom Hanks", "born": 1956 }
     *
     * @param request validated DTO with name and optional born year
     * @return 201 Created with the persisted Person
     */
    @PostMapping
    public ResponseEntity<Person> createPerson(@Valid @RequestBody CreatePersonRequest request) {
        Person saved = personService.createPerson(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Returns all Person nodes.
     *
     * GET /api/persons
     *
     * @return 200 OK with list of all persons
     */
    @GetMapping
    public ResponseEntity<List<Person>> getAllPersons() {
        return ResponseEntity.ok(personService.findAll());
    }

    /**
     * Finds a Person by their internal Neo4j ID.
     *
     * GET /api/persons/{id}
     *
     * @param id the Neo4j-generated node ID
     * @return 200 OK with the Person, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Person> getPersonById(@PathVariable Long id) {
        return personService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Finds a Person by their name.
     *
     * GET /api/persons/search?name=Tom%20Hanks
     *
     * @param name the person's full name
     * @return 200 OK with the Person, or 404 Not Found
     */
    @GetMapping("/search")
    public ResponseEntity<Person> getPersonByName(@RequestParam String name) {
        return personService.findByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Finds persons born within a year range.
     *
     * GET /api/persons/born?minYear=1960&maxYear=1980
     *
     * @param minYear minimum birth year (inclusive)
     * @param maxYear maximum birth year (inclusive)
     * @return 200 OK with matching persons
     */
    @GetMapping("/born")
    public ResponseEntity<List<Person>> getPersonsByBornRange(
            @RequestParam int minYear,
            @RequestParam int maxYear) {
        return ResponseEntity.ok(personService.findByBornBetween(minYear, maxYear));
    }

    /**
     * Deletes a Person node by ID.
     *
     * DELETE /api/persons/{id}
     *
     * @param id the Neo4j-generated node ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePerson(@PathVariable Long id) {
        personService.deletePerson(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates an ACTED_IN relationship between a Person and a Movie.
     *
     * POST /api/persons/{personName}/acted-in/{movieTitle}
     *
     * This endpoint adds a directed graph edge:
     * (Person)-[:ACTED_IN]->(Movie)
     *
     * @param personName the actor's name (URL-encoded if it contains spaces)
     * @param movieTitle the movie title (URL-encoded if it contains spaces)
     * @return 200 OK with the updated Person node
     */
    @PostMapping("/{personName}/acted-in/{movieTitle}")
    public ResponseEntity<Person> addActedIn(
            @PathVariable String personName,
            @PathVariable String movieTitle) {
        Person updated = personService.addActedIn(personName, movieTitle);
        return ResponseEntity.ok(updated);
    }

    /**
     * Creates a DIRECTED relationship between a Person and a Movie.
     *
     * POST /api/persons/{personName}/directed/{movieTitle}
     *
     * @param personName the director's name
     * @param movieTitle the movie title
     * @return 200 OK with the updated Person node
     */
    @PostMapping("/{personName}/directed/{movieTitle}")
    public ResponseEntity<Person> addDirected(
            @PathVariable String personName,
            @PathVariable String movieTitle) {
        Person updated = personService.addDirected(personName, movieTitle);
        return ResponseEntity.ok(updated);
    }

    /**
     * Creates a FOLLOWS relationship between two Person nodes.
     *
     * POST /api/persons/{followerName}/follows/{followedName}
     *
     * Graph edge: (follower)-[:FOLLOWS]->(followed)
     *
     * @param followerName the person who follows
     * @param followedName the person being followed
     * @return 200 OK with the updated follower Person node
     */
    @PostMapping("/{followerName}/follows/{followedName}")
    public ResponseEntity<Person> addFollows(
            @PathVariable String followerName,
            @PathVariable String followedName) {
        Person updated = personService.addFollows(followerName, followedName);
        return ResponseEntity.ok(updated);
    }

    /**
     * Returns all persons who acted in a given movie (graph traversal).
     *
     * GET /api/persons/actors-in/{movieTitle}
     *
     * @param movieTitle the movie title
     * @return 200 OK with list of actors
     */
    @GetMapping("/actors-in/{movieTitle}")
    public ResponseEntity<List<Person>> getActorsByMovie(@PathVariable String movieTitle) {
        return ResponseEntity.ok(personService.findActorsByMovie(movieTitle));
    }

    /**
     * Returns all persons who directed a given movie (graph traversal).
     *
     * GET /api/persons/directors-of/{movieTitle}
     *
     * @param movieTitle the movie title
     * @return 200 OK with list of directors
     */
    @GetMapping("/directors-of/{movieTitle}")
    public ResponseEntity<List<Person>> getDirectorsByMovie(@PathVariable String movieTitle) {
        return ResponseEntity.ok(personService.findDirectorsByMovie(movieTitle));
    }

    /**
     * Returns all persons that a given person follows.
     *
     * GET /api/persons/{personName}/following
     *
     * @param personName the follower's name
     * @return 200 OK with list of followed persons
     */
    @GetMapping("/{personName}/following")
    public ResponseEntity<List<Person>> getFollowing(@PathVariable String personName) {
        return ResponseEntity.ok(personService.findFollowedBy(personName));
    }

    /**
     * Global error handler for IllegalArgumentException (entity not found by name).
     *
     * @param ex the exception
     * @return 404 Not Found with error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
