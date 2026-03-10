package com.example.dockercomposesupport.integration;

import com.example.dockercomposesupport.repository.BookRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the book catalogue API using Testcontainers.
 *
 * <h2>Key concepts demonstrated</h2>
 * <ul>
 *   <li>{@code @Testcontainers} — activates the Testcontainers JUnit 5 extension,
 *       which manages the lifecycle of {@code @Container} fields.</li>
 *   <li>{@code @Container} — declares a PostgreSQL Docker container scoped to this
 *       test class. It is started once before the first test and stopped after the
 *       last, saving the overhead of per-test container restarts.</li>
 *   <li>{@code @DynamicPropertySource} — bridges the container's ephemeral host/port
 *       into the Spring context before it starts, so Spring Data JPA connects to the
 *       containerised PostgreSQL instead of a real (or missing) database.</li>
 *   <li>{@code @SpringBootTest} — loads the full application context.</li>
 *   <li>{@code @AutoConfigureMockMvc} — wires up a {@link MockMvc} instance for
 *       HTTP-level testing without starting a real network socket.</li>
 * </ul>
 *
 * <h2>Docker Compose vs Testcontainers during tests</h2>
 * <p>Spring Boot's Docker Compose integration is disabled for tests via
 * {@code spring.docker.compose.skip.in-tests=true} in {@code application.yml}.
 * This prevents Spring Boot from launching {@code compose.yml} while Testcontainers
 * is already providing a PostgreSQL container. Both mechanisms cannot run
 * simultaneously for the same service.</p>
 *
 * <h2>Test isolation</h2>
 * <p>The repository is cleared in {@code @BeforeEach} so every test starts with
 * an empty database. This avoids ordering dependencies between tests.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Book Catalogue Integration Tests")
class BookIntegrationTest {

    /**
     * PostgreSQL Testcontainers container.
     *
     * <p>The {@code static} modifier means it is shared across all test methods
     * in this class (class-level lifecycle). Starting once is much faster than
     * starting per method.</p>
     *
     * <p>We use the {@code postgres:16-alpine} image — the same version used in
     * {@code compose.yml} and {@code docker-compose.yml}, ensuring parity between
     * dev, test and production environments.</p>
     */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("booksdb_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    /**
     * Registers the container's connection properties into the Spring context.
     *
     * <p>This static method runs after the container starts but <em>before</em>
     * the Spring ApplicationContext is created. It overrides any datasource
     * settings from {@code application.yml} with the actual values from
     * the Testcontainers container (ephemeral host + mapped port).</p>
     *
     * @param registry the dynamic property registry provided by Spring Test
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // Override the datasource URL with the container's connection URL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Use create-drop during tests so the schema is fresh on every run
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Disable Spring Boot Docker Compose integration during tests
        // (Testcontainers is providing the PostgreSQL instance instead)
        registry.add("spring.docker.compose.enabled", () -> "false");
    }

    /** MockMvc allows HTTP-level testing against the full Spring context. */
    @Autowired
    private MockMvc mockMvc;

    /** Direct repository access used for cleanup between tests. */
    @Autowired
    private BookRepository bookRepository;

    /**
     * Clears all books before every test to ensure test isolation.
     * Without this, books created in one test would be visible in another.
     */
    @BeforeEach
    void cleanDatabase() {
        bookRepository.deleteAll();
    }

    // =========================================================================
    // POST /api/books — create a book
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("POST /api/books — should create a book and return 201")
    void createBook_returnsCreated() throws Exception {
        String requestBody = """
                {
                    "title": "Clean Code",
                    "author": "Robert C. Martin",
                    "isbn": "978-0132350884",
                    "publicationYear": 2008,
                    "description": "A handbook of agile software craftsmanship"
                }
                """;

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.author").value("Robert C. Martin"))
                .andExpect(jsonPath("$.isbn").value("978-0132350884"))
                .andExpect(jsonPath("$.publicationYear").value(2008))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/books — should return 400 when title is blank")
    void createBook_returns400_whenTitleBlank() throws Exception {
        String requestBody = """
                {
                    "title": "",
                    "author": "Some Author",
                    "isbn": "ISBN-001"
                }
                """;

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.title").isNotEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/books — should return 400 when isbn is missing")
    void createBook_returns400_whenIsbnMissing() throws Exception {
        String requestBody = """
                {
                    "title": "Some Book",
                    "author": "Some Author"
                }
                """;

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.isbn").isNotEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/books — should return 409 when title is duplicate")
    void createBook_returns409_whenTitleDuplicate() throws Exception {
        // First create succeeds
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Unique Title",
                                    "author": "Author One",
                                    "isbn": "ISBN-AAA"
                                }
                                """))
                .andExpect(status().isCreated());

        // Second create with same title returns 409
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Unique Title",
                                    "author": "Author Two",
                                    "isbn": "ISBN-BBB"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("Unique Title")));
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/books — should return 409 when ISBN is duplicate")
    void createBook_returns409_whenIsbnDuplicate() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "First Book",
                                    "author": "Author A",
                                    "isbn": "DUPE-ISBN"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Second Book",
                                    "author": "Author B",
                                    "isbn": "DUPE-ISBN"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("DUPE-ISBN")));
    }

    // =========================================================================
    // GET /api/books — list all books
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("GET /api/books — should return empty list when no books exist")
    void listBooks_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/books — should return all books")
    void listBooks_returnsAllBooks() throws Exception {
        // Seed two books
        seedBook("Effective Java", "Joshua Bloch", "ISBN-EJ", 2018);
        seedBook("The Pragmatic Programmer", "Andrew Hunt", "ISBN-PP", 1999);

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].title",
                        containsInAnyOrder("Effective Java", "The Pragmatic Programmer")));
    }

    @Test
    @Order(12)
    @DisplayName("GET /api/books?author=Bloch — should filter by author")
    void listBooks_filtersByAuthor() throws Exception {
        seedBook("Effective Java", "Joshua Bloch", "ISBN-EJ", 2018);
        seedBook("Refactoring", "Martin Fowler", "ISBN-RF", 2018);

        mockMvc.perform(get("/api/books").param("author", "Bloch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].author").value("Joshua Bloch"));
    }

    @Test
    @Order(13)
    @DisplayName("GET /api/books?year=2018 — should filter by publication year")
    void listBooks_filtersByYear() throws Exception {
        seedBook("Effective Java", "Joshua Bloch", "ISBN-EJ", 2018);
        seedBook("Refactoring", "Martin Fowler", "ISBN-RF", 2018);
        seedBook("Clean Code", "Robert Martin", "ISBN-CC", 2008);

        mockMvc.perform(get("/api/books").param("year", "2018"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].publicationYear", everyItem(is(2018))));
    }

    @Test
    @Order(14)
    @DisplayName("GET /api/books?q=java — should return keyword-matched books")
    void listBooks_keywordSearch() throws Exception {
        seedBook("Effective Java", "Joshua Bloch", "ISBN-EJ", 2018);
        seedBook("Python Crash Course", "Eric Matthes", "ISBN-PY", 2019);

        mockMvc.perform(get("/api/books").param("q", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Effective Java"));
    }

    // =========================================================================
    // GET /api/books/{id}
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("GET /api/books/{id} — should return book when found")
    void getBook_returnsBook_whenFound() throws Exception {
        // Seed and capture the created book's ID
        String location = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Domain-Driven Design",
                                    "author": "Eric Evans",
                                    "isbn": "ISBN-DDD",
                                    "publicationYear": 2003
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract the ID from the response JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Long id = mapper.readTree(location).get("id").asLong();

        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Domain-Driven Design"))
                .andExpect(jsonPath("$.author").value("Eric Evans"));
    }

    @Test
    @Order(21)
    @DisplayName("GET /api/books/{id} — should return 404 for unknown ID")
    void getBook_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/books/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("9999")));
    }

    // =========================================================================
    // PUT /api/books/{id}
    // =========================================================================

    @Test
    @Order(30)
    @DisplayName("PUT /api/books/{id} — should update book and return 200")
    void updateBook_returnsUpdatedBook() throws Exception {
        // Seed a book
        String created = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Old Title","author":"Old Author","isbn":"ISBN-OLD"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Long id = mapper.readTree(created).get("id").asLong();

        // Update it
        mockMvc.perform(put("/api/books/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "New Title",
                                    "author": "New Author",
                                    "isbn": "ISBN-NEW",
                                    "publicationYear": 2024
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.author").value("New Author"))
                .andExpect(jsonPath("$.publicationYear").value(2024));
    }

    @Test
    @Order(31)
    @DisplayName("PUT /api/books/{id} — should return 404 for unknown ID")
    void updateBook_returns404_whenNotFound() throws Exception {
        mockMvc.perform(put("/api/books/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"T","author":"A","isbn":"I"}
                                """))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // DELETE /api/books/{id}
    // =========================================================================

    @Test
    @Order(40)
    @DisplayName("DELETE /api/books/{id} — should delete book and return 204")
    void deleteBook_returnsNoContent() throws Exception {
        // Seed a book
        String created = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"To Delete","author":"Author","isbn":"ISBN-DEL"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Long id = mapper.readTree(created).get("id").asLong();

        // Delete it
        mockMvc.perform(delete("/api/books/{id}", id))
                .andExpect(status().isNoContent());

        // Verify it is gone
        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(41)
    @DisplayName("DELETE /api/books/{id} — should return 404 for unknown ID")
    void deleteBook_returns404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/books/9999"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Full round-trip test
    // =========================================================================

    @Test
    @Order(50)
    @DisplayName("Full round-trip: create → list → get → update → delete")
    void fullRoundTrip() throws Exception {
        // 1. Create a book
        String created = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Round Trip Book",
                                    "author": "Test Author",
                                    "isbn": "ISBN-RT-001",
                                    "publicationYear": 2024,
                                    "description": "Full round-trip test book"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Long id = mapper.readTree(created).get("id").asLong();

        // 2. Verify it appears in the list
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Round Trip Book")));

        // 3. Get by ID
        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Full round-trip test book"));

        // 4. Update it
        mockMvc.perform(put("/api/books/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Round Trip Book Updated",
                                    "author": "Updated Author",
                                    "isbn": "ISBN-RT-001",
                                    "publicationYear": 2025,
                                    "description": "Updated description"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Round Trip Book Updated"))
                .andExpect(jsonPath("$.publicationYear").value(2025));

        // 5. Delete it
        mockMvc.perform(delete("/api/books/{id}", id))
                .andExpect(status().isNoContent());

        // 6. Verify it is gone from the list
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", not(hasItem("Round Trip Book Updated"))));
    }

    // =========================================================================
    // Helper method
    // =========================================================================

    /**
     * Seeds a book directly via the API for use in test setups.
     *
     * @param title  book title
     * @param author book author
     * @param isbn   book ISBN
     * @param year   publication year
     */
    private void seedBook(String title, String author, String isbn, int year) throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"title":"%s","author":"%s","isbn":"%s","publicationYear":%d}
                                """, title, author, isbn, year)))
                .andExpect(status().isCreated());
    }
}
