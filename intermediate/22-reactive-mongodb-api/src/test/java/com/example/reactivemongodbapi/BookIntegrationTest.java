package com.example.reactivemongodbapi;

import com.example.reactivemongodbapi.domain.Book;
import com.example.reactivemongodbapi.dto.BookRequest;
import com.example.reactivemongodbapi.repository.BookRepository;
import com.example.reactivemongodbapi.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the Reactive MongoDB API.
 *
 * <p>This test class verifies end-to-end behaviour from the HTTP layer through the
 * service and repository layers down to a real MongoDB instance managed by Testcontainers.
 *
 * <p>Key aspects:
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded
 *       Netty server on a random port. The full WebFlux filter chain, exception
 *       handlers, and serialisation stack are active — identical to production.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up a MongoDB Docker
 *       container shared across all test methods to avoid the overhead of restarting
 *       MongoDB for each individual test.</li>
 *   <li>{@link DynamicPropertySource} injects the container's connection URI into
 *       the Spring {@link org.springframework.core.env.Environment} before the
 *       application context is created. This overrides the default localhost settings
 *       so the Reactive MongoDB driver connects to the container instead.</li>
 *   <li>{@link WebTestClient} is the reactive-aware HTTP test client from Spring
 *       WebFlux. Unlike {@code TestRestTemplate} (which blocks), WebTestClient
 *       integrates naturally with the reactive pipeline and provides a fluent
 *       assertion API.</li>
 *   <li>The {@code "integration-test"} profile activates separate logging config
 *       to reduce test output noise.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Book integration tests (Reactive MongoDB + WebFlux API)")
class BookIntegrationTest {

    // ── Testcontainers MongoDB container ──────────────────────────────────────────

    /**
     * A MongoDB 7 container shared by all tests in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses a single container
     * for the entire test class lifecycle, avoiding the significant overhead of
     * starting/stopping MongoDB for each test method.
     *
     * <p>We use the official {@code mongo:7.0} image. MongoDB 7 is the current LTS
     * release with improved performance and Queryable Encryption support.
     */
    @Container
    static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    /**
     * Register the container's dynamic connection URI into the Spring
     * {@link org.springframework.core.env.Environment} before the application
     * context is created.
     *
     * <p>The Reactive MongoDB auto-configuration reads
     * {@code spring.data.mongodb.uri} to build the reactive {@code MongoClient}.
     * By setting it here, we point the entire application context to the Testcontainers
     * MongoDB instance instead of a localhost MongoDB.
     *
     * @param registry the property registry Spring Boot reads before context startup
     */
    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        // getReplicaSetUrl() returns the full MongoDB connection string including
        // the host, port, and database name assigned by Testcontainers.
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /**
     * {@link WebTestClient} wired to the embedded Netty server started by
     * {@link SpringBootTest}. Spring Boot auto-configures this bean when
     * {@code WebEnvironment.RANDOM_PORT} is used.
     */
    @Autowired
    WebTestClient webTestClient;

    /**
     * Direct access to the service layer for test data setup, bypassing HTTP
     * serialisation overhead for the "Given" steps.
     */
    @Autowired
    BookService bookService;

    /**
     * Direct access to the repository for teardown (clearing all documents before
     * each test to ensure test isolation).
     */
    @Autowired
    BookRepository bookRepository;

    // ── Setup ─────────────────────────────────────────────────────────────────────

    /**
     * Clear the books collection before each test to ensure test isolation.
     *
     * <p>Without this, documents created in one test would affect subsequent tests,
     * causing non-deterministic failures (e.g., count assertions failing because
     * a previous test inserted extra documents).
     *
     * <p>{@code .block()} waits for the reactive delete to complete before the test
     * method starts — necessary because {@code @BeforeEach} is not reactive-aware.
     */
    @BeforeEach
    void setUp() {
        bookRepository.deleteAll().block();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /**
     * Build a valid {@link BookRequest} with unique ISBN for test isolation.
     *
     * @param title  book title
     * @param author author name
     * @param isbn   unique ISBN for this test
     * @param price  price as a string (e.g., "12.99")
     * @return a fully populated BookRequest
     */
    private BookRequest book(String title, String author, String isbn, double price) {
        return new BookRequest(title, author, isbn, price, 2000,
                List.of("fiction"), "A book description.", "English", 200, true);
    }

    // ── GET /api/books ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books returns 200 with empty array when collection is empty")
    void getAllBooks_returns200WithEmptyArray_whenCollectionIsEmpty() {
        webTestClient.get()
                .uri("/api/books")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Book.class).hasSize(0);
    }

    @Test
    @DisplayName("GET /api/books returns 200 with all books when documents exist")
    void getAllBooks_returns200WithAllBooks() {
        // Given: two books in the collection
        bookService.create(book("Book One", "Author A", "ISBN-001", 10.00)).block();
        bookService.create(book("Book Two", "Author B", "ISBN-002", 20.00)).block();

        // When / Then
        webTestClient.get()
                .uri("/api/books")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Book.class).hasSize(2);
    }

    // ── GET /api/books/{id} ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books/{id} returns 200 with the book when found")
    void getBookById_returns200_whenFound() {
        // Given: a persisted book
        Book created = bookService.create(book("Find Me", "Author", "ISBN-003", 15.00)).block();

        // When / Then
        webTestClient.get()
                .uri("/api/books/{id}", created.getId())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Book.class)
                .value(b -> {
                    assertThat(b.getTitle()).isEqualTo("Find Me");
                    assertThat(b.getId()).isEqualTo(created.getId());
                });
    }

    @Test
    @DisplayName("GET /api/books/{id} returns 404 when the book does not exist")
    void getBookById_returns404_whenNotFound() {
        webTestClient.get()
                .uri("/api/books/000000000000000000000000")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── GET /api/books/isbn/{isbn} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books/isbn/{isbn} returns 200 when the ISBN exists")
    void getBookByIsbn_returns200_whenFound() {
        // Given: a book with a known ISBN
        bookService.create(book("ISBN Test Book", "Author", "ISBN-TEST-001", 9.99)).block();

        // When / Then
        webTestClient.get()
                .uri("/api/books/isbn/ISBN-TEST-001")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Book.class)
                .value(b -> assertThat(b.getIsbn()).isEqualTo("ISBN-TEST-001"));
    }

    @Test
    @DisplayName("GET /api/books/isbn/{isbn} returns 404 when the ISBN does not exist")
    void getBookByIsbn_returns404_whenNotFound() {
        webTestClient.get()
                .uri("/api/books/isbn/NONEXISTENT-ISBN")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── GET /api/books/author/{author} ────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books/author/{author} returns books by that author")
    void getBooksByAuthor_returnsMatchingBooks() {
        // Given: one book by Orwell and one by Huxley
        bookService.create(book("1984", "George Orwell", "ISBN-004", 12.99)).block();
        bookService.create(book("Brave New World", "Aldous Huxley", "ISBN-005", 11.99)).block();

        // When / Then: only Orwell's book is returned
        webTestClient.get()
                .uri("/api/books/author/George Orwell")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Book.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getAuthor()).isEqualTo("George Orwell"));
    }

    // ── GET /api/books/available ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books/available returns only available books")
    void getAvailableBooks_returnsOnlyAvailable() {
        // Given: one available and one unavailable book
        bookService.create(new BookRequest("Available Book", "Author", "ISBN-006",
                10.00, 2020, List.of(), "Desc", "English", 100, true)).block();
        bookService.create(new BookRequest("Unavailable Book", "Author", "ISBN-007",
                10.00, 2020, List.of(), "Desc", "English", 100, false)).block();

        // When / Then: only the available book is returned
        webTestClient.get()
                .uri("/api/books/available")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Book.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).isAvailable()).isTrue());
    }

    // ── GET /api/books/year/{year} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books/year/{year} returns books published in that year")
    void getBooksByYear_returnsMatchingBooks() {
        // Given: one book from 1949 and one from 1945
        bookService.create(new BookRequest("1984", "Orwell", "ISBN-008",
                12.99, 1949, List.of(), "Desc", "English", 328, true)).block();
        bookService.create(new BookRequest("Animal Farm", "Orwell", "ISBN-009",
                9.99, 1945, List.of(), "Desc", "English", 112, true)).block();

        // When / Then: only the 1949 book is returned
        webTestClient.get()
                .uri("/api/books/year/1949")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Book.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getPublishedYear()).isEqualTo(1949));
    }

    // ── GET /api/books/genre/{genre} ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books/genre/{genre} returns books with that genre in their array")
    void getBooksByGenre_returnsMatchingBooks() {
        // Given: a dystopia book and a satire book
        bookService.create(new BookRequest("1984", "Orwell", "ISBN-010",
                12.99, 1949, List.of("fiction", "dystopia"),
                "Desc", "English", 328, true)).block();
        bookService.create(new BookRequest("Animal Farm", "Orwell", "ISBN-011",
                9.99, 1945, List.of("satire", "fiction"),
                "Desc", "English", 112, true)).block();

        // When / Then: only the dystopia book is returned for the "dystopia" genre
        webTestClient.get()
                .uri("/api/books/genre/dystopia")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Book.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getGenres()).contains("dystopia"));
    }

    // ── GET /api/books/search?keyword=... ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books/search?keyword=... returns matching books case-insensitively")
    void searchByTitle_returnsMatchingBooks_caseInsensitive() {
        // Given: two books with different titles
        bookService.create(book("Nineteen Eighty-Four", "Orwell", "ISBN-012", 12.99)).block();
        bookService.create(book("Brave New World", "Huxley", "ISBN-013", 11.99)).block();

        // When: search for "EIGHTY" (uppercase)
        webTestClient.get()
                .uri(uri -> uri.path("/api/books/search").queryParam("keyword", "EIGHTY").build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Book.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getTitle()).containsIgnoringCase("eighty"));
    }

    // ── GET /api/books/price-range?min=...&max=... ────────────────────────────────

    @Test
    @DisplayName("GET /api/books/price-range returns books within the price range")
    void getBooksByPriceRange_returnsBookInRange() {
        // Given: books at different price points
        bookService.create(book("Cheap Book", "Author", "ISBN-014", 5.00)).block();
        bookService.create(book("Mid Book", "Author", "ISBN-015", 25.00)).block();
        bookService.create(book("Expensive Book", "Author", "ISBN-016", 99.99)).block();

        // When: filter between 15.00 and 50.00
        webTestClient.get()
                .uri(uri -> uri.path("/api/books/price-range")
                        .queryParam("min", "15.00")
                        .queryParam("max", "50.00")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Book.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getTitle()).isEqualTo("Mid Book"));
    }

    // ── GET /api/books/author/{author}/count ──────────────────────────────────────

    @Test
    @DisplayName("GET /api/books/author/{author}/count returns the correct count")
    void countByAuthor_returnsCorrectCount() {
        // Given: three books by Orwell
        bookService.create(book("Book A", "George Orwell", "ISBN-017", 10.00)).block();
        bookService.create(book("Book B", "George Orwell", "ISBN-018", 11.00)).block();
        bookService.create(book("Book C", "George Orwell", "ISBN-019", 12.00)).block();

        // When / Then: count is 3
        webTestClient.get()
                .uri("/api/books/author/George Orwell/count")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(3L);
    }

    // ── POST /api/books ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/books returns 201 with the created book including generated ObjectId")
    void createBook_returns201WithCreatedBook() {
        // Given: a valid book request
        BookRequest request = new BookRequest(
                "Dune", "Frank Herbert", "978-0-441-17271-9",
                14.99, 1965,
                List.of("science fiction", "space opera"),
                "A science fiction novel about the desert planet Arrakis.",
                "English", 412, true);

        // When / Then
        webTestClient.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Book.class)
                .value(saved -> {
                    assertThat(saved.getId()).isNotNull().isNotBlank();
                    assertThat(saved.getTitle()).isEqualTo("Dune");
                    assertThat(saved.getAuthor()).isEqualTo("Frank Herbert");
                    assertThat(saved.getIsbn()).isEqualTo("978-0-441-17271-9");
                    assertThat(saved.getPrice()).isEqualTo(14.99);
                    assertThat(saved.isAvailable()).isTrue();
                    assertThat(saved.getCreatedAt()).isNotNull();
                    assertThat(saved.getUpdatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("POST /api/books returns 400 when the title is blank")
    void createBook_returns400_whenTitleIsBlank() {
        BookRequest invalid = new BookRequest("", "Author", "ISBN-020",
                10.00, 2020, List.of(), "Desc", "English", 100, true);

        webTestClient.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/books returns 400 when price is null")
    void createBook_returns400_whenPriceIsNull() {
        String invalidJson = "{\"title\":\"Book\",\"author\":\"Author\",\"isbn\":\"ISBN-021\"," +
                "\"price\":null,\"publishedYear\":2020,\"description\":\"Desc\"," +
                "\"language\":\"English\",\"pageCount\":100,\"available\":true}";

        webTestClient.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidJson)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/books returns 409 when a book with the same ISBN already exists")
    void createBook_returns409_whenDuplicateIsbn() {
        // Given: a book with this ISBN already exists
        bookService.create(book("First Book", "Author", "ISBN-DUP", 10.00)).block();

        // When: try to create another book with the same ISBN
        BookRequest duplicate = book("Second Book", "Author", "ISBN-DUP", 15.00);

        // Then: conflict response
        webTestClient.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(duplicate)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    // ── PUT /api/books/{id} ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/books/{id} returns 200 with the updated book")
    void updateBook_returns200WithUpdatedBook() {
        // Given: an existing book
        Book created = bookService.create(
                book("Old Title", "Old Author", "ISBN-022", 10.00)).block();

        BookRequest updateRequest = new BookRequest(
                "New Title", "New Author", "ISBN-022",
                20.00, 2023,
                List.of("updated genre"), "Updated description.", "Spanish", 250, false);

        // When / Then
        webTestClient.put()
                .uri("/api/books/{id}", created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Book.class)
                .value(updated -> {
                    assertThat(updated.getTitle()).isEqualTo("New Title");
                    assertThat(updated.getAuthor()).isEqualTo("New Author");
                    assertThat(updated.getPrice()).isEqualTo(20.00);
                    assertThat(updated.getLanguage()).isEqualTo("Spanish");
                    assertThat(updated.isAvailable()).isFalse();
                    assertThat(updated.getId()).isEqualTo(created.getId());
                });
    }

    @Test
    @DisplayName("PUT /api/books/{id} returns 404 when the book does not exist")
    void updateBook_returns404_whenNotFound() {
        BookRequest request = book("Title", "Author", "ISBN-023", 10.00);

        webTestClient.put()
                .uri("/api/books/000000000000000000000000")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── DELETE /api/books/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/books/{id} returns 204 and the book is no longer retrievable")
    void deleteBook_returns204AndBookGone() {
        // Given: a persisted book
        Book created = bookService.create(
                book("Delete Me", "Author", "ISBN-024", 5.00)).block();

        // When: delete via HTTP
        webTestClient.delete()
                .uri("/api/books/{id}", created.getId())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NO_CONTENT);

        webTestClient.get()
                .uri("/api/books/{id}", created.getId())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("DELETE /api/books/{id} returns 404 when the book does not exist")
    void deleteBook_returns404_whenNotFound() {
        webTestClient.delete()
                .uri("/api/books/000000000000000000000000")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── Service-layer MongoDB verification ────────────────────────────────────────

    @Test
    @DisplayName("Created book is persisted and retrievable from MongoDB")
    void createdBook_isPersistedInMongoDB() {
        // Given: a book created via the service layer
        Book created = bookService.create(
                book("Persisted Book", "Author", "ISBN-025", 19.99)).block();

        // When: retrieve directly from MongoDB via the service
        Book found = bookService.findById(created.getId()).block();

        // Then: the book exists with the correct data
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Persisted Book");
        assertThat(found.isAvailable()).isTrue();
        assertThat(found.getId()).isNotNull().isNotBlank();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Updated book reflects new values in MongoDB")
    void updatedBook_reflectsNewValuesInMongoDB() {
        // Given: a book with initial values
        Book created = bookService.create(
                book("Before Update", "Old Author", "ISBN-026", 10.00)).block();

        // When: update the book
        bookService.update(created.getId(), new BookRequest(
                "After Update", "New Author", "ISBN-026",
                20.00, 2023, List.of(), "New desc.", "French", 300, false)).block();

        // Then: re-fetching from MongoDB shows the new values
        Book updated = bookService.findById(created.getId()).block();
        assertThat(updated).isNotNull();
        assertThat(updated.getTitle()).isEqualTo("After Update");
        assertThat(updated.getAuthor()).isEqualTo("New Author");
        assertThat(updated.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Deleted book is no longer present in MongoDB")
    void deletedBook_isRemovedFromMongoDB() {
        // Given: a book in MongoDB
        Book created = bookService.create(
                book("Temp Book", "Author", "ISBN-027", 1.00)).block();
        String id = created.getId();

        // When: delete the book
        Boolean deleted = bookService.deleteById(id).block();

        // Then: the book is gone
        assertThat(deleted).isTrue();
        assertThat(bookService.findById(id).block()).isNull();
        assertThat(bookRepository.count().block()).isZero();
    }
}
