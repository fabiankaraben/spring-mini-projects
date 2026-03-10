package com.example.neo4jgraphapi.service;

import com.example.neo4jgraphapi.domain.Movie;
import com.example.neo4jgraphapi.dto.CreateMovieRequest;
import com.example.neo4jgraphapi.repository.MovieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Movie-related operations.
 *
 * <p>Encapsulates all business logic for Movie nodes. Uses
 * {@link MovieRepository} for persistence and custom Cypher queries.</p>
 */
@Service
public class MovieService {

    /** Repository for Movie node CRUD and custom Cypher queries. */
    private final MovieRepository movieRepository;

    /** Constructor injection for testability. */
    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    /**
     * Creates a new Movie node in the graph.
     *
     * @param request DTO containing title, release year, and optional tagline
     * @return the persisted Movie with its generated Neo4j ID
     */
    @Transactional
    public Movie createMovie(CreateMovieRequest request) {
        Movie movie = new Movie(request.getTitle(), request.getReleased(), request.getTagline());
        return movieRepository.save(movie);
    }

    /**
     * Returns all Movie nodes in the graph.
     *
     * @return list of all movies
     */
    @Transactional(readOnly = true)
    public List<Movie> findAll() {
        return movieRepository.findAll();
    }

    /**
     * Finds a Movie by its internal Neo4j node ID.
     *
     * @param id the node's generated ID
     * @return Optional Movie
     */
    @Transactional(readOnly = true)
    public Optional<Movie> findById(Long id) {
        return movieRepository.findById(id);
    }

    /**
     * Finds a Movie by its title.
     *
     * @param title the movie title
     * @return Optional Movie
     */
    @Transactional(readOnly = true)
    public Optional<Movie> findByTitle(String title) {
        return movieRepository.findByTitle(title);
    }

    /**
     * Finds all movies released in a specific year.
     *
     * @param year the release year
     * @return list of matching movies
     */
    @Transactional(readOnly = true)
    public List<Movie> findByYear(int year) {
        return movieRepository.findByReleased(year);
    }

    /**
     * Finds all movies released within a year range.
     *
     * @param minYear minimum year (inclusive)
     * @param maxYear maximum year (inclusive)
     * @return list of matching movies
     */
    @Transactional(readOnly = true)
    public List<Movie> findByYearRange(int minYear, int maxYear) {
        return movieRepository.findByReleasedBetween(minYear, maxYear);
    }

    /**
     * Deletes a Movie node by ID.
     *
     * @param id the node's generated ID
     */
    @Transactional
    public void deleteMovie(Long id) {
        movieRepository.deleteById(id);
    }

    /**
     * Returns movies acted in by a given person (two-hop graph query).
     *
     * @param personName the actor's name
     * @return list of movies
     */
    @Transactional(readOnly = true)
    public List<Movie> findMoviesActedInByPerson(String personName) {
        return movieRepository.findMoviesActedInByPerson(personName);
    }

    /**
     * Returns movies directed by a given person.
     *
     * @param personName the director's name
     * @return list of movies
     */
    @Transactional(readOnly = true)
    public List<Movie> findMoviesDirectedByPerson(String personName) {
        return movieRepository.findMoviesDirectedByPerson(personName);
    }

    /**
     * Returns movie recommendations for a person based on their co-actors.
     *
     * <p>The recommendation algorithm uses a two-hop traversal:
     * person → ACTED_IN → movie ← ACTED_IN ← co-actor → ACTED_IN → recommendation</p>
     *
     * @param personName the person to generate recommendations for
     * @return list of recommended movies
     */
    @Transactional(readOnly = true)
    public List<Movie> findRecommendations(String personName) {
        return movieRepository.findRecommendationsForPerson(personName);
    }
}
