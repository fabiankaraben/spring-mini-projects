package com.example.neo4jgraphapi.controller;

import com.example.neo4jgraphapi.domain.Movie;
import com.example.neo4jgraphapi.dto.CreateMovieRequest;
import com.example.neo4jgraphapi.service.MovieService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing endpoints for Movie node operations.
 *
 * <p>Base path: {@code /api/movies}</p>
 *
 * <p>Covers standard CRUD as well as graph-traversal queries such as
 * "movies acted in by a person" and "movie recommendations".</p>
 */
@RestController
@RequestMapping("/api/movies")
public class MovieController {

    /** Service handling all Movie-related business logic. */
    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    /**
     * Creates a new Movie node.
     *
     * POST /api/movies
     * Body: { "title": "The Matrix", "released": 1999, "tagline": "Welcome to the Real World." }
     *
     * @param request validated DTO with title, optional released year and tagline
     * @return 201 Created with the persisted Movie
     */
    @PostMapping
    public ResponseEntity<Movie> createMovie(@Valid @RequestBody CreateMovieRequest request) {
        Movie saved = movieService.createMovie(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Returns all Movie nodes.
     *
     * GET /api/movies
     *
     * @return 200 OK with list of all movies
     */
    @GetMapping
    public ResponseEntity<List<Movie>> getAllMovies() {
        return ResponseEntity.ok(movieService.findAll());
    }

    /**
     * Finds a Movie by its internal Neo4j ID.
     *
     * GET /api/movies/{id}
     *
     * @param id the Neo4j-generated node ID
     * @return 200 OK with the Movie, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Movie> getMovieById(@PathVariable Long id) {
        return movieService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Finds a Movie by its title.
     *
     * GET /api/movies/search?title=The%20Matrix
     *
     * @param title the exact movie title
     * @return 200 OK with the Movie, or 404 Not Found
     */
    @GetMapping("/search")
    public ResponseEntity<Movie> getMovieByTitle(@RequestParam String title) {
        return movieService.findByTitle(title)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Finds all movies released in a specific year.
     *
     * GET /api/movies/year/{year}
     *
     * @param year the release year
     * @return 200 OK with matching movies
     */
    @GetMapping("/year/{year}")
    public ResponseEntity<List<Movie>> getMoviesByYear(@PathVariable int year) {
        return ResponseEntity.ok(movieService.findByYear(year));
    }

    /**
     * Finds all movies released within a year range.
     *
     * GET /api/movies/year-range?minYear=1990&maxYear=2000
     *
     * @param minYear minimum release year (inclusive)
     * @param maxYear maximum release year (inclusive)
     * @return 200 OK with matching movies
     */
    @GetMapping("/year-range")
    public ResponseEntity<List<Movie>> getMoviesByYearRange(
            @RequestParam int minYear,
            @RequestParam int maxYear) {
        return ResponseEntity.ok(movieService.findByYearRange(minYear, maxYear));
    }

    /**
     * Deletes a Movie node by ID.
     *
     * DELETE /api/movies/{id}
     *
     * @param id the Neo4j-generated node ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMovie(@PathVariable Long id) {
        movieService.deleteMovie(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns movies acted in by a specific person (graph traversal).
     *
     * GET /api/movies/acted-in-by/{personName}
     *
     * Traverses: (Person {name})-[:ACTED_IN]->(Movie)
     *
     * @param personName the actor's name
     * @return 200 OK with list of movies
     */
    @GetMapping("/acted-in-by/{personName}")
    public ResponseEntity<List<Movie>> getMoviesActedInBy(@PathVariable String personName) {
        return ResponseEntity.ok(movieService.findMoviesActedInByPerson(personName));
    }

    /**
     * Returns movies directed by a specific person (graph traversal).
     *
     * GET /api/movies/directed-by/{personName}
     *
     * Traverses: (Person {name})-[:DIRECTED]->(Movie)
     *
     * @param personName the director's name
     * @return 200 OK with list of movies
     */
    @GetMapping("/directed-by/{personName}")
    public ResponseEntity<List<Movie>> getMoviesDirectedBy(@PathVariable String personName) {
        return ResponseEntity.ok(movieService.findMoviesDirectedByPerson(personName));
    }

    /**
     * Returns movie recommendations for a person based on their co-actors.
     *
     * GET /api/movies/recommendations/{personName}
     *
     * Uses a two-hop Cypher traversal to find movies that co-actors have been in
     * but the given person has not.
     *
     * @param personName the person to recommend movies for
     * @return 200 OK with list of recommended movies
     */
    @GetMapping("/recommendations/{personName}")
    public ResponseEntity<List<Movie>> getRecommendations(@PathVariable String personName) {
        return ResponseEntity.ok(movieService.findRecommendations(personName));
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
