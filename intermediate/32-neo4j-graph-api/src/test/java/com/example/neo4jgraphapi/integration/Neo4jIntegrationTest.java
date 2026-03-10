package com.example.neo4jgraphapi.integration;

import com.example.neo4jgraphapi.domain.Movie;
import com.example.neo4jgraphapi.domain.Person;
import com.example.neo4jgraphapi.repository.MovieRepository;
import com.example.neo4jgraphapi.repository.PersonRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests using Testcontainers to spin up a real Neo4j instance.
 *
 * <p>Key concepts demonstrated:</p>
 * <ul>
 *   <li>{@code @Testcontainers} — activates Testcontainers JUnit 5 extension</li>
 *   <li>{@code @Container} — declares a Neo4j Docker container scoped to the test class</li>
 *   <li>{@code @DynamicPropertySource} — dynamically injects the container's bolt URI and
 *       credentials into the Spring context, overriding application.yml values</li>
 *   <li>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)} with {@code @AutoConfigureMockMvc}
 *       — boots the full Spring context and allows HTTP-level testing via MockMvc</li>
 * </ul>
 *
 * <p>The Neo4j Testcontainers image used is {@code neo4j:5} (Community Edition).
 * The admin password must be set explicitly to satisfy Neo4j's security requirements.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Neo4j Graph API Integration Tests")
class Neo4jIntegrationTest {

    /**
     * Testcontainers Neo4j container.
     *
     * <p>The {@code @Container} annotation with a static field means one container
     * instance is shared across all tests in this class (class-scoped lifecycle),
     * which is faster than creating a new container per test.</p>
     *
     * <p>We use Neo4j 5 Community Edition. The admin password must match what
     * we inject via {@link #configureNeo4j}.</p>
     */
    @Container
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5")
            .withoutAuthentication(); // Disables auth for simplicity in tests

    /**
     * Dynamically injects the running container's bolt URI into the Spring context.
     *
     * <p>This overrides the {@code spring.neo4j.uri} from application.yml with the
     * actual port assigned by the container (Docker chooses a random host port).</p>
     *
     * @param registry the Spring dynamic property registry
     */
    @DynamicPropertySource
    static void configureNeo4j(DynamicPropertyRegistry registry) {
        // getBoltUrl() returns e.g. "bolt://localhost:49152"
        registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl);
        // No authentication — set empty username/password
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "");
    }

    /** MockMvc allows us to make HTTP requests against the full Spring context. */
    @Autowired
    private MockMvc mockMvc;

    /** Direct repository access for test data setup and assertions. */
    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private MovieRepository movieRepository;

    /**
     * Clears all nodes and relationships before each test to ensure test isolation.
     * Without this, data created in one test would affect subsequent tests.
     */
    @BeforeEach
    void cleanDatabase() {
        personRepository.deleteAll();
        movieRepository.deleteAll();
    }

    // =========================================================================
    // Movie CRUD Integration Tests
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("POST /api/movies — should create a movie node in Neo4j")
    void createMovie_persistsToNeo4j() throws Exception {
        // Arrange: JSON request body
        String requestBody = """
                {
                    "title": "The Matrix",
                    "released": 1999,
                    "tagline": "Welcome to the Real World."
                }
                """;

        // Act + Assert: call the endpoint and verify 201 + JSON response
        mockMvc.perform(post("/api/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("The Matrix"))
                .andExpect(jsonPath("$.released").value(1999))
                .andExpect(jsonPath("$.tagline").value("Welcome to the Real World."))
                .andExpect(jsonPath("$.id").isNumber()); // Neo4j assigned an ID

        // Verify directly in the database
        assertThat(movieRepository.findByTitle("The Matrix")).isPresent();
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/movies — should return all movies from Neo4j")
    void getAllMovies_returnsPersistedMovies() throws Exception {
        // Arrange: seed two movies directly via the repository
        movieRepository.save(new Movie("The Matrix", 1999));
        movieRepository.save(new Movie("John Wick", 2014));

        // Act + Assert
        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("The Matrix", "John Wick")));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/movies/search?title=... — should return movie by title")
    void getMovieByTitle_returnsCorrectMovie() throws Exception {
        movieRepository.save(new Movie("The Matrix", 1999, "Welcome to the Real World."));

        mockMvc.perform(get("/api/movies/search").param("title", "The Matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("The Matrix"))
                .andExpect(jsonPath("$.released").value(1999));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/movies/search?title=... — should return 404 when not found")
    void getMovieByTitle_returns404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/movies/search").param("title", "NonExistentMovie"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/movies/year/{year} — should return movies for given year")
    void getMoviesByYear_returnsCorrectMovies() throws Exception {
        movieRepository.save(new Movie("The Matrix", 1999));
        movieRepository.save(new Movie("John Wick", 2014));

        mockMvc.perform(get("/api/movies/year/1999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("The Matrix"));
    }

    @Test
    @Order(6)
    @DisplayName("DELETE /api/movies/{id} — should remove movie from Neo4j")
    void deleteMovie_removesFromNeo4j() throws Exception {
        Movie saved = movieRepository.save(new Movie("The Matrix", 1999));

        mockMvc.perform(delete("/api/movies/" + saved.getId()))
                .andExpect(status().isNoContent());

        assertThat(movieRepository.findByTitle("The Matrix")).isEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("POST /api/movies — should return 400 when title is blank")
    void createMovie_returns400_whenTitleIsBlank() throws Exception {
        String requestBody = """
                {
                    "title": "",
                    "released": 1999
                }
                """;

        mockMvc.perform(post("/api/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Person CRUD Integration Tests
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("POST /api/persons — should create a person node in Neo4j")
    void createPerson_persistsToNeo4j() throws Exception {
        String requestBody = """
                {
                    "name": "Keanu Reeves",
                    "born": 1964
                }
                """;

        mockMvc.perform(post("/api/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Keanu Reeves"))
                .andExpect(jsonPath("$.born").value(1964));

        assertThat(personRepository.findByName("Keanu Reeves")).isPresent();
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/persons — should return all persons")
    void getAllPersons_returnsPersistedPersons() throws Exception {
        personRepository.save(new Person("Keanu Reeves", 1964));
        personRepository.save(new Person("Laurence Fishburne", 1961));

        mockMvc.perform(get("/api/persons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name",
                        containsInAnyOrder("Keanu Reeves", "Laurence Fishburne")));
    }

    @Test
    @Order(12)
    @DisplayName("GET /api/persons/search?name=... — should return person by name")
    void getPersonByName_returnsCorrectPerson() throws Exception {
        personRepository.save(new Person("Keanu Reeves", 1964));

        mockMvc.perform(get("/api/persons/search").param("name", "Keanu Reeves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keanu Reeves"))
                .andExpect(jsonPath("$.born").value(1964));
    }

    @Test
    @Order(13)
    @DisplayName("GET /api/persons/born?minYear=...&maxYear=... — should return persons in range")
    void getPersonsByBornRange_returnsCorrectPersons() throws Exception {
        personRepository.save(new Person("Keanu Reeves", 1964));
        personRepository.save(new Person("Lana Wachowski", 1965));
        personRepository.save(new Person("Laurence Fishburne", 1961));

        mockMvc.perform(get("/api/persons/born")
                        .param("minYear", "1963")
                        .param("maxYear", "1966"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name",
                        containsInAnyOrder("Keanu Reeves", "Lana Wachowski")));
    }

    // =========================================================================
    // Graph Relationship Integration Tests
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("POST /api/persons/{person}/acted-in/{movie} — should create ACTED_IN edge")
    void addActedIn_createsRelationshipInGraph() throws Exception {
        // Arrange: create person and movie nodes first
        personRepository.save(new Person("Keanu Reeves", 1964));
        movieRepository.save(new Movie("The Matrix", 1999));

        // Act: create the relationship via the REST API
        mockMvc.perform(post("/api/persons/Keanu Reeves/acted-in/The Matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keanu Reeves"))
                .andExpect(jsonPath("$.actedIn[0].title").value("The Matrix"));

        // Verify: the graph traversal query now returns Keanu for "The Matrix"
        assertThat(personRepository.findActorsByMovieTitle("The Matrix"))
                .extracting(Person::getName)
                .contains("Keanu Reeves");
    }

    @Test
    @Order(21)
    @DisplayName("POST /api/persons/{person}/directed/{movie} — should create DIRECTED edge")
    void addDirected_createsRelationshipInGraph() throws Exception {
        personRepository.save(new Person("Lana Wachowski", 1965));
        movieRepository.save(new Movie("The Matrix", 1999));

        mockMvc.perform(post("/api/persons/Lana Wachowski/directed/The Matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lana Wachowski"))
                .andExpect(jsonPath("$.directed[0].title").value("The Matrix"));

        assertThat(personRepository.findDirectorsByMovieTitle("The Matrix"))
                .extracting(Person::getName)
                .contains("Lana Wachowski");
    }

    @Test
    @Order(22)
    @DisplayName("POST /api/persons/{follower}/follows/{followed} — should create FOLLOWS edge")
    void addFollows_createsSocialEdge() throws Exception {
        personRepository.save(new Person("Keanu Reeves", 1964));
        personRepository.save(new Person("Laurence Fishburne", 1961));

        mockMvc.perform(post("/api/persons/Keanu Reeves/follows/Laurence Fishburne"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keanu Reeves"))
                .andExpect(jsonPath("$.follows[0].name").value("Laurence Fishburne"));

        assertThat(personRepository.findFollowedBy("Keanu Reeves"))
                .extracting(Person::getName)
                .contains("Laurence Fishburne");
    }

    @Test
    @Order(23)
    @DisplayName("GET /api/persons/actors-in/{movie} — should return actors via graph traversal")
    void getActorsByMovie_traversesGraph() throws Exception {
        // Arrange: build the graph
        Movie matrix = movieRepository.save(new Movie("The Matrix", 1999));
        Person keanu = new Person("Keanu Reeves", 1964);
        keanu.getActedIn().add(matrix);
        personRepository.save(keanu);
        Person laurence = new Person("Laurence Fishburne", 1961);
        laurence.getActedIn().add(matrix);
        personRepository.save(laurence);

        // Act + Assert
        mockMvc.perform(get("/api/persons/actors-in/The Matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name",
                        containsInAnyOrder("Keanu Reeves", "Laurence Fishburne")));
    }

    @Test
    @Order(24)
    @DisplayName("GET /api/movies/acted-in-by/{person} — should return movies via graph traversal")
    void getMoviesActedInByPerson_traversesGraph() throws Exception {
        Movie matrix = movieRepository.save(new Movie("The Matrix", 1999));
        Movie johnWick = movieRepository.save(new Movie("John Wick", 2014));
        Person keanu = new Person("Keanu Reeves", 1964);
        keanu.getActedIn().add(matrix);
        keanu.getActedIn().add(johnWick);
        personRepository.save(keanu);

        mockMvc.perform(get("/api/movies/acted-in-by/Keanu Reeves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].title",
                        containsInAnyOrder("The Matrix", "John Wick")));
    }

    @Test
    @Order(25)
    @DisplayName("GET /api/movies/recommendations/{person} — should return co-actor recommendations")
    void getRecommendations_returnsCoActorMovies() throws Exception {
        // Build a graph where:
        //   Keanu & Laurence both acted in The Matrix
        //   Laurence also acted in Othello (not Keanu)
        // Expected recommendation for Keanu: Othello
        Movie matrix = movieRepository.save(new Movie("The Matrix", 1999));
        Movie othello = movieRepository.save(new Movie("Othello", 1995));

        Person keanu = new Person("Keanu Reeves", 1964);
        keanu.getActedIn().add(matrix);

        Person laurence = new Person("Laurence Fishburne", 1961);
        laurence.getActedIn().add(matrix);
        laurence.getActedIn().add(othello);

        personRepository.save(keanu);
        personRepository.save(laurence);

        // Keanu's co-actor Laurence acted in Othello which Keanu hasn't → recommendation
        mockMvc.perform(get("/api/movies/recommendations/Keanu Reeves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Othello"));
    }

    @Test
    @Order(26)
    @DisplayName("POST /api/persons/{person}/acted-in/{movie} — should return 404 when person not found")
    void addActedIn_returns404WhenPersonNotFound() throws Exception {
        movieRepository.save(new Movie("The Matrix", 1999));

        mockMvc.perform(post("/api/persons/Unknown Person/acted-in/The Matrix"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("Person not found")));
    }
}
