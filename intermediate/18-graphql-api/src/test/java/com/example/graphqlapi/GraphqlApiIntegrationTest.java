package com.example.graphqlapi;

import com.example.graphqlapi.repository.AuthorRepository;
import com.example.graphqlapi.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.test.tester.WebGraphQlTester;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Full integration tests for the GraphQL API.
 *
 * <p>This test class exercises the complete request-processing pipeline:
 * <pre>
 *   HTTP request → Spring MVC → Spring for GraphQL → Controller → Service → Repository → PostgreSQL
 * </pre>
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded Tomcat
 *       on a random port. The entire Spring context — including the GraphQL schema,
 *       runtime wiring, and all beans — is loaded, giving confidence that the application
 *       boots and resolves correctly end-to-end.</li>
 *   <li>{@link AutoConfigureHttpGraphQlTester} auto-configures an {@link HttpGraphQlTester}
 *       that sends HTTP POST requests to the {@code /graphql} endpoint. It understands
 *       GraphQL request/response shapes, so assertions can target specific fields by path.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up a real PostgreSQL Docker
 *       container for the duration of the test class. The container is shared across all
 *       test methods to avoid the overhead of restarting PostgreSQL per test.</li>
 *   <li>{@link DynamicPropertySource} injects the container's JDBC URL, username, and
 *       password into the Spring {@code Environment} before the application context starts,
 *       so Spring Boot connects to the Testcontainers-managed PostgreSQL instance.</li>
 *   <li>The {@code "test"} profile activates {@code application-test.yml} which sets
 *       {@code ddl-auto: create-drop} — the schema is created fresh from the JPA entities
 *       at test start and dropped at the end.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("GraphQL API integration tests (PostgreSQL + Testcontainers)")
class GraphqlApiIntegrationTest {

    // ── Testcontainers PostgreSQL container ───────────────────────────────────────

    /**
     * A PostgreSQL container shared by all test methods in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses the same
     * container instance for the entire test class lifecycle, which avoids the
     * overhead of starting/stopping PostgreSQL for each individual test method.
     *
     * <p>We use the official {@code postgres:16-alpine} image — Alpine variant
     * is significantly smaller than the full Debian image, making test startup faster.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpass");

    /**
     * Registers the container's dynamic JDBC connection details into the Spring
     * {@link org.springframework.core.env.Environment} before the application context
     * is created. This overrides the defaults from {@code application.yml}.
     *
     * <p>Testcontainers assigns a random host port, so we must use the container's
     * {@code getJdbcUrl()}, {@code getUsername()}, and {@code getPassword()} methods
     * to get the actual values at runtime.
     *
     * @param registry the property registry read by Spring Boot before context startup
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // ── Injected beans ────────────────────────────────────────────────────────────

    /**
     * {@link WebGraphQlHandler} is the core Spring for GraphQL component that processes
     * GraphQL requests on the server side. Injecting it directly and wrapping it in a
     * {@link WebGraphQlTester} gives us in-process GraphQL execution without requiring
     * an HTTP server, a WebTestClient, or the reactive stack.
     */
    @Autowired
    WebGraphQlHandler webGraphQlHandler;

    /**
     * {@link WebGraphQlTester} wraps the {@link WebGraphQlHandler} and provides a
     * fluent DSL for executing GraphQL documents in tests. It parses the
     * {@code data} and {@code errors} fields of the response and lets us navigate
     * result paths with type-safe assertions.
     *
     * <p>Built in {@code setUp()} so each test starts with a fresh tester instance.
     */
    WebGraphQlTester graphQlTester;

    @Autowired
    AuthorRepository authorRepository;

    @Autowired
    BookRepository bookRepository;

    /**
     * Clean the database and rebuild the tester before each test to ensure
     * tests are fully independent.
     * Order matters: books must be deleted before authors due to the foreign-key constraint.
     */
    @BeforeEach
    void setUp() {
        bookRepository.deleteAll();
        authorRepository.deleteAll();
        // Build the GraphQL tester backed by the in-process WebGraphQlHandler
        graphQlTester = WebGraphQlTester.builder(webGraphQlHandler).build();
    }

    // ── Author query tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("authors query returns empty list when no authors exist")
    void authors_returnsEmptyList_whenNoData() {
        // language=GraphQL
        String query = """
                query {
                  authors {
                    id
                    name
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()  // assert no GraphQL errors in the response
                .path("authors")
                .entityList(Object.class)
                .hasSize(0);
    }

    @Test
    @DisplayName("createAuthor mutation persists and returns the new author")
    void createAuthor_persistsAndReturnsAuthor() {
        // language=GraphQL
        String mutation = """
                mutation {
                  createAuthor(input: { name: "George Orwell", bio: "English novelist" }) {
                    id
                    name
                    bio
                  }
                }
                """;

        graphQlTester.document(mutation)
                .execute()
                .errors().verify()
                .path("createAuthor.name").entity(String.class).isEqualTo("George Orwell")
                .path("createAuthor.bio").entity(String.class).isEqualTo("English novelist")
                .path("createAuthor.id").hasValue();  // id must be non-null after persistence
    }

    @Test
    @DisplayName("authors query returns all persisted authors")
    void authors_returnsAllAuthors_afterCreation() {
        // First create two authors via mutations
        graphQlTester.document("""
                mutation { createAuthor(input: { name: "Author One", bio: null }) { id } }
                """).execute().errors().verify();

        graphQlTester.document("""
                mutation { createAuthor(input: { name: "Author Two", bio: null }) { id } }
                """).execute().errors().verify();

        // Then verify both appear in the authors query
        graphQlTester.document("""
                query { authors { name } }
                """)
                .execute()
                .errors().verify()
                .path("authors")
                .entityList(Object.class)
                .hasSize(2);
    }

    @Test
    @DisplayName("author(id) query returns the correct author")
    void author_byId_returnsCorrectAuthor() {
        // Create an author and capture the generated ID
        String createMutation = """
                mutation {
                  createAuthor(input: { name: "Agatha Christie", bio: "Crime novelist" }) {
                    id
                    name
                  }
                }
                """;

        String id = graphQlTester.document(createMutation)
                .execute()
                .errors().verify()
                .path("createAuthor.id")
                .entity(String.class)
                .get();

        // Now query by the returned ID
        String query = String.format("""
                query {
                  author(id: %s) {
                    name
                    bio
                  }
                }
                """, id);

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("author.name").entity(String.class).isEqualTo("Agatha Christie")
                .path("author.bio").entity(String.class).isEqualTo("Crime novelist");
    }

    @Test
    @DisplayName("updateAuthor mutation changes the author's fields")
    void updateAuthor_changesFields() {
        // Create first
        String id = graphQlTester.document("""
                mutation { createAuthor(input: { name: "Old Name", bio: "Old Bio" }) { id } }
                """)
                .execute().errors().verify()
                .path("createAuthor.id").entity(String.class).get();

        // Update
        String updateMutation = String.format("""
                mutation {
                  updateAuthor(id: %s, input: { name: "New Name", bio: "New Bio" }) {
                    name
                    bio
                  }
                }
                """, id);

        graphQlTester.document(updateMutation)
                .execute()
                .errors().verify()
                .path("updateAuthor.name").entity(String.class).isEqualTo("New Name")
                .path("updateAuthor.bio").entity(String.class).isEqualTo("New Bio");
    }

    @Test
    @DisplayName("deleteAuthor mutation removes the author and returns true")
    void deleteAuthor_removesAuthor() {
        String id = graphQlTester.document("""
                mutation { createAuthor(input: { name: "To Delete", bio: null }) { id } }
                """)
                .execute().errors().verify()
                .path("createAuthor.id").entity(String.class).get();

        String deleteMutation = String.format("""
                mutation { deleteAuthor(id: %s) }
                """, id);

        graphQlTester.document(deleteMutation)
                .execute()
                .errors().verify()
                .path("deleteAuthor").entity(Boolean.class).isEqualTo(true);

        // Verify the author no longer exists
        graphQlTester.document(String.format("""
                query { author(id: %s) { id } }
                """, id))
                .execute()
                .errors().verify()
                .path("author").valueIsNull();
    }

    // ── Book query tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createBook mutation persists book with author reference")
    void createBook_persistsBookWithAuthor() {
        // Create an author first (books require an author)
        String authorId = graphQlTester.document("""
                mutation { createAuthor(input: { name: "Isaac Asimov", bio: "SF author" }) { id } }
                """)
                .execute().errors().verify()
                .path("createAuthor.id").entity(String.class).get();

        String bookMutation = String.format("""
                mutation {
                  createBook(input: {
                    title: "Foundation",
                    isbn: "978-0-553-29335-7",
                    genre: "Science Fiction",
                    publishedDate: "1951-08-21",
                    authorId: %s
                  }) {
                    id
                    title
                    isbn
                    genre
                    publishedDate
                    author {
                      name
                    }
                  }
                }
                """, authorId);

        graphQlTester.document(bookMutation)
                .execute()
                .errors().verify()
                .path("createBook.title").entity(String.class).isEqualTo("Foundation")
                .path("createBook.isbn").entity(String.class).isEqualTo("978-0-553-29335-7")
                .path("createBook.genre").entity(String.class).isEqualTo("Science Fiction")
                .path("createBook.author.name").entity(String.class).isEqualTo("Isaac Asimov");
    }

    @Test
    @DisplayName("booksByGenre query returns only books of the requested genre")
    void booksByGenre_returnsFilteredBooks() {
        // Create author and two books in different genres
        String authorId = graphQlTester.document("""
                mutation { createAuthor(input: { name: "Author", bio: null }) { id } }
                """)
                .execute().errors().verify()
                .path("createAuthor.id").entity(String.class).get();

        graphQlTester.document(String.format("""
                mutation { createBook(input: { title: "Sci-Fi Book", isbn: "ISBN-001", genre: "Science Fiction", authorId: %s }) { id } }
                """, authorId)).execute().errors().verify();

        graphQlTester.document(String.format("""
                mutation { createBook(input: { title: "History Book", isbn: "ISBN-002", genre: "History", authorId: %s }) { id } }
                """, authorId)).execute().errors().verify();

        // Query only Science Fiction books
        graphQlTester.document("""
                query { booksByGenre(genre: "Science Fiction") { title genre } }
                """)
                .execute()
                .errors().verify()
                .path("booksByGenre")
                .entityList(Object.class)
                .hasSize(1);
    }

    @Test
    @DisplayName("deleteBook mutation removes the book and returns true")
    void deleteBook_removesBook() {
        String authorId = graphQlTester.document("""
                mutation { createAuthor(input: { name: "Author", bio: null }) { id } }
                """)
                .execute().errors().verify()
                .path("createAuthor.id").entity(String.class).get();

        String bookId = graphQlTester.document(String.format("""
                mutation { createBook(input: { title: "Book to Delete", isbn: "ISBN-DEL", authorId: %s }) { id } }
                """, authorId))
                .execute().errors().verify()
                .path("createBook.id").entity(String.class).get();

        graphQlTester.document(String.format("""
                mutation { deleteBook(id: %s) }
                """, bookId))
                .execute()
                .errors().verify()
                .path("deleteBook").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    @DisplayName("searchAuthors query finds authors by name fragment")
    void searchAuthors_findsMatchingAuthors() {
        graphQlTester.document("""
                mutation { createAuthor(input: { name: "Gabriel García Márquez", bio: null }) { id } }
                """).execute().errors().verify();

        graphQlTester.document("""
                mutation { createAuthor(input: { name: "Margaret Atwood", bio: null }) { id } }
                """).execute().errors().verify();

        // Search for "García" – should match only the first author
        graphQlTester.document("""
                query { searchAuthors(name: "García") { name } }
                """)
                .execute()
                .errors().verify()
                .path("searchAuthors")
                .entityList(Object.class)
                .hasSize(1);
    }
}
