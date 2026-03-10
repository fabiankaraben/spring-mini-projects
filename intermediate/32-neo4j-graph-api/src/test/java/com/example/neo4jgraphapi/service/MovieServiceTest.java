package com.example.neo4jgraphapi.service;

import com.example.neo4jgraphapi.domain.Movie;
import com.example.neo4jgraphapi.dto.CreateMovieRequest;
import com.example.neo4jgraphapi.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MovieService}.
 *
 * <p>Uses Mockito to replace the real {@link MovieRepository} with a mock,
 * allowing tests to run instantly without any database or Spring context.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MovieService unit tests")
class MovieServiceTest {

    /** Mock repository — no real Neo4j connection. */
    @Mock
    private MovieRepository movieRepository;

    /** The real service under test, with mock injected. */
    @InjectMocks
    private MovieService movieService;

    /** Shared test fixtures. */
    private Movie matrix;
    private Movie johnWick;

    @BeforeEach
    void setUp() {
        matrix = new Movie("The Matrix", 1999, "Welcome to the Real World.");
        matrix.setId(1L);

        johnWick = new Movie("John Wick", 2014, "Don't set him off.");
        johnWick.setId(2L);
    }

    // -------------------------------------------------------------------------
    // createMovie tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createMovie should map DTO to entity and persist it")
    void createMovie_mapsAndPersists() {
        CreateMovieRequest request = new CreateMovieRequest("The Matrix", 1999, "Welcome to the Real World.");
        when(movieRepository.save(any(Movie.class))).thenReturn(matrix);

        Movie result = movieService.createMovie(request);

        assertThat(result.getTitle()).isEqualTo("The Matrix");
        assertThat(result.getReleased()).isEqualTo(1999);
        assertThat(result.getTagline()).isEqualTo("Welcome to the Real World.");
        verify(movieRepository, times(1)).save(any(Movie.class));
    }

    @Test
    @DisplayName("createMovie with null tagline should save successfully")
    void createMovie_withNullTagline_saves() {
        CreateMovieRequest request = new CreateMovieRequest("Untitled", 2024, null);
        Movie saved = new Movie("Untitled", 2024, null);
        when(movieRepository.save(any(Movie.class))).thenReturn(saved);

        Movie result = movieService.createMovie(request);

        assertThat(result.getTagline()).isNull();
    }

    // -------------------------------------------------------------------------
    // findAll tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAll should return all movies from repository")
    void findAll_returnsAllMovies() {
        when(movieRepository.findAll()).thenReturn(List.of(matrix, johnWick));

        List<Movie> result = movieService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("The Matrix", "John Wick");
    }

    @Test
    @DisplayName("findAll should return empty list when no movies exist")
    void findAll_returnsEmptyList() {
        when(movieRepository.findAll()).thenReturn(List.of());

        assertThat(movieService.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findById tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findById should return movie when found")
    void findById_returnsMovieWhenFound() {
        when(movieRepository.findById(1L)).thenReturn(Optional.of(matrix));

        Optional<Movie> result = movieService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("The Matrix");
    }

    @Test
    @DisplayName("findById should return empty when not found")
    void findById_returnsEmptyWhenNotFound() {
        when(movieRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(movieService.findById(999L)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByTitle tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByTitle should return matching movie")
    void findByTitle_returnsMatchingMovie() {
        when(movieRepository.findByTitle("The Matrix")).thenReturn(Optional.of(matrix));

        Optional<Movie> result = movieService.findByTitle("The Matrix");

        assertThat(result).isPresent();
        assertThat(result.get().getReleased()).isEqualTo(1999);
    }

    // -------------------------------------------------------------------------
    // findByYear tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByYear should return movies released in that year")
    void findByYear_returnsMoviesForYear() {
        when(movieRepository.findByReleased(1999)).thenReturn(List.of(matrix));

        List<Movie> result = movieService.findByYear(1999);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("The Matrix");
    }

    // -------------------------------------------------------------------------
    // findByYearRange tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByYearRange should return movies within range")
    void findByYearRange_returnsMoviesInRange() {
        when(movieRepository.findByReleasedBetween(1990, 2000)).thenReturn(List.of(matrix));

        List<Movie> result = movieService.findByYearRange(1990, 2000);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReleased()).isBetween(1990, 2000);
    }

    // -------------------------------------------------------------------------
    // deleteMovie tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deleteMovie should call deleteById on repository")
    void deleteMovie_callsDeleteById() {
        doNothing().when(movieRepository).deleteById(1L);

        movieService.deleteMovie(1L);

        verify(movieRepository, times(1)).deleteById(1L);
    }

    // -------------------------------------------------------------------------
    // findMoviesActedInByPerson tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findMoviesActedInByPerson should return movies for actor")
    void findMoviesActedInByPerson_returnsMoviesForActor() {
        when(movieRepository.findMoviesActedInByPerson("Keanu Reeves"))
                .thenReturn(List.of(matrix, johnWick));

        List<Movie> result = movieService.findMoviesActedInByPerson("Keanu Reeves");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("The Matrix", "John Wick");
    }

    // -------------------------------------------------------------------------
    // findRecommendations tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findRecommendations should return co-actor movie recommendations")
    void findRecommendations_returnsRecommendedMovies() {
        // The recommendation query finds movies that co-actors have been in
        when(movieRepository.findRecommendationsForPerson("Keanu Reeves"))
                .thenReturn(List.of(johnWick));

        List<Movie> result = movieService.findRecommendations("Keanu Reeves");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("John Wick");
    }

    @Test
    @DisplayName("findRecommendations should return empty list when no co-actors found")
    void findRecommendations_returnsEmptyList_whenNoCoActors() {
        when(movieRepository.findRecommendationsForPerson("Solo Actor"))
                .thenReturn(List.of());

        assertThat(movieService.findRecommendations("Solo Actor")).isEmpty();
    }
}
