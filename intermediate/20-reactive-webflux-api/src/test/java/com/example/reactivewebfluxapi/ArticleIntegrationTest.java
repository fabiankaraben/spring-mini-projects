package com.example.reactivewebfluxapi;

import com.example.reactivewebfluxapi.domain.Article;
import com.example.reactivewebfluxapi.dto.ArticleRequest;
import com.example.reactivewebfluxapi.repository.ArticleRepository;
import com.example.reactivewebfluxapi.service.ArticleService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the Reactive WebFlux Article API.
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
 *   <li>{@link DynamicPropertySource} injects the Testcontainers-managed MongoDB URI
 *       into the Spring {@link org.springframework.core.env.Environment} before the
 *       application context is created, so the reactive MongoDB driver connects to
 *       the container instead of any locally installed MongoDB.</li>
 *   <li>{@link WebTestClient} is the reactive-aware HTTP test client provided by
 *       Spring WebFlux. Unlike {@code TestRestTemplate} (which blocks), WebTestClient
 *       supports both blocking (via {@code .block()}) and reactive assertion styles.
 *       Its fluent API integrates naturally with the reactive pipeline under test.</li>
 *   <li>The {@code "test"} profile activates {@code application-test.yml} which
 *       reduces logging noise during test runs.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Article integration tests (MongoDB + Reactive WebFlux API)")
class ArticleIntegrationTest {

    // ── Testcontainers MongoDB container ──────────────────────────────────────────

    /**
     * A MongoDB container shared by all tests in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses a single container
     * for the entire test class lifecycle, avoiding the significant overhead of
     * starting/stopping MongoDB for each test method.
     *
     * <p>We use the official {@code mongo:7.0} image, which matches common production
     * environments and provides the replica-set URL needed by the reactive MongoDB driver.
     */
    @Container
    static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    /**
     * Register the container's dynamic connection URI into the Spring
     * {@link org.springframework.core.env.Environment} before the application context
     * is created.
     *
     * <p>Testcontainers assigns a random host port, so we use
     * {@code getReplicaSetUrl()} which returns a complete URI
     * (e.g. {@code mongodb://localhost:32768/test}). This URI takes precedence over
     * the individual host/port/database properties in {@code application-test.yml}.
     *
     * @param registry the property registry Spring Boot reads before context startup
     */
    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /**
     * {@link WebTestClient} wired to the embedded Netty server started by
     * {@link SpringBootTest}. Spring Boot auto-configures this bean when
     * {@code WebEnvironment.RANDOM_PORT} is used.
     *
     * <p>Unlike {@code TestRestTemplate}, {@link WebTestClient}:
     * <ul>
     *   <li>Is non-blocking by nature (built on Project Reactor).</li>
     *   <li>Provides a fluent assertion DSL for verifying response status,
     *       headers, and body.</li>
     *   <li>Returns 4xx/5xx responses without throwing exceptions, allowing
     *       negative-path assertions.</li>
     * </ul>
     */
    @Autowired
    WebTestClient webTestClient;

    /**
     * Direct access to the service layer for test data setup, bypassing HTTP
     * serialisation overhead for the "Given" steps.
     */
    @Autowired
    ArticleService articleService;

    /**
     * Direct access to the repository for teardown (clearing all documents before
     * each test to ensure test isolation).
     */
    @Autowired
    ArticleRepository articleRepository;

    // ── Setup ─────────────────────────────────────────────────────────────────────

    /**
     * Clear the articles collection before each test.
     *
     * <p>Without this, documents created in one test would be visible to subsequent
     * tests, causing non-deterministic failures (e.g., GET all returning more
     * articles than expected because a previous test created some).
     *
     * <p>{@code .block()} waits for the reactive delete to complete before the test
     * method starts — necessary here because {@code @BeforeEach} is not reactive-aware.
     */
    @BeforeEach
    void setUp() {
        articleRepository.deleteAll().block();
    }

    // ── GET /api/articles ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/articles returns 200 with empty array when collection is empty")
    void getAllArticles_returns200WithEmptyArray_whenCollectionIsEmpty() {
        webTestClient.get()
                .uri("/api/articles")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                // Verify HTTP status
                .expectStatus().isOk()
                // Verify the body is an empty JSON array
                .expectBodyList(Article.class).hasSize(0);
    }

    @Test
    @DisplayName("GET /api/articles returns 200 with all articles when documents exist")
    void getAllArticles_returns200WithAllArticles() {
        // Given: two articles in the database
        articleService.create(new ArticleRequest("Article One", "Content 1", "Author A", "technology", true)).block();
        articleService.create(new ArticleRequest("Article Two", "Content 2", "Author B", "science", false)).block();

        // When / Then
        webTestClient.get()
                .uri("/api/articles")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Article.class).hasSize(2);
    }

    // ── GET /api/articles/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/articles/{id} returns 200 with the article when found")
    void getArticleById_returns200_whenFound() {
        // Given: a persisted article
        Article created = articleService.create(
                new ArticleRequest("Find Me", "Body text", "Author X", "technology", true)).block();

        // When / Then
        webTestClient.get()
                .uri("/api/articles/{id}", created.getId())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Article.class)
                .value(a -> {
                    assertThat(a.getTitle()).isEqualTo("Find Me");
                    assertThat(a.getAuthor()).isEqualTo("Author X");
                    assertThat(a.getId()).isEqualTo(created.getId());
                });
    }

    @Test
    @DisplayName("GET /api/articles/{id} returns 404 when the article does not exist")
    void getArticleById_returns404_whenNotFound() {
        webTestClient.get()
                .uri("/api/articles/507f1f77bcf86cd799439999")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── GET /api/articles/category/{category} ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/articles/category/{category} returns articles in the category")
    void getByCategory_returnsArticlesInCategory() {
        // Given: one technology and one science article
        articleService.create(new ArticleRequest("Tech Article", "Body", "Author", "technology", true)).block();
        articleService.create(new ArticleRequest("Science Article", "Body", "Author", "science", true)).block();

        // When / Then: only the technology article is returned
        webTestClient.get()
                .uri("/api/articles/category/technology")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Article.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getCategory()).isEqualTo("technology"));
    }

    // ── GET /api/articles/search?keyword=... ──────────────────────────────────────

    @Test
    @DisplayName("GET /api/articles/search?keyword=... returns matching articles case-insensitively")
    void searchByTitle_returnsMatchingArticles_caseInsensitive() {
        // Given: two articles, one whose title contains "reactive"
        articleService.create(new ArticleRequest("Reactive Programming Guide", "Body", "Author", "technology", true)).block();
        articleService.create(new ArticleRequest("Spring Boot Basics", "Body", "Author", "technology", true)).block();

        // When: search for "REACTIVE" (uppercase)
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/articles/search").queryParam("keyword", "REACTIVE").build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Article.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getTitle()).isEqualTo("Reactive Programming Guide"));
    }

    // ── GET /api/articles/published ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/articles/published returns only published articles")
    void getPublished_returnsOnlyPublishedArticles() {
        // Given: one published and one draft article
        articleService.create(new ArticleRequest("Published Article", "Body", "Author", "tech", true)).block();
        articleService.create(new ArticleRequest("Draft Article", "Body", "Author", "tech", false)).block();

        // When / Then: only the published article is returned
        webTestClient.get()
                .uri("/api/articles/published")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Article.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).isPublished()).isTrue());
    }

    // ── GET /api/articles/author/{author} ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/articles/author/{author} returns articles by the given author")
    void getByAuthor_returnsArticlesByAuthor() {
        // Given: two articles by Jane, one by Bob
        articleService.create(new ArticleRequest("Jane's Article 1", "Body", "Jane Doe", "tech", true)).block();
        articleService.create(new ArticleRequest("Jane's Article 2", "Body", "Jane Doe", "science", true)).block();
        articleService.create(new ArticleRequest("Bob's Article", "Body", "Bob Smith", "tech", true)).block();

        // When / Then: only Jane's two articles
        webTestClient.get()
                .uri("/api/articles/author/{author}", "Jane Doe")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Article.class)
                .hasSize(2)
                .value(list -> list.forEach(a -> assertThat(a.getAuthor()).isEqualTo("Jane Doe")));
    }

    // ── GET /api/articles/author/{author}/count ───────────────────────────────────

    @Test
    @DisplayName("GET /api/articles/author/{author}/count returns the article count for the author")
    void countByAuthor_returnsCorrectCount() {
        // Given: three articles by Jane
        articleService.create(new ArticleRequest("A1", "B", "Jane Doe", "tech", true)).block();
        articleService.create(new ArticleRequest("A2", "B", "Jane Doe", "tech", true)).block();
        articleService.create(new ArticleRequest("A3", "B", "Jane Doe", "tech", true)).block();

        // When / Then: the count is 3
        webTestClient.get()
                .uri("/api/articles/author/{author}/count", "Jane Doe")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(3L);
    }

    // ── POST /api/articles ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/articles returns 201 with the created article including generated ID")
    void createArticle_returns201WithCreatedArticle() {
        // Given: a valid article request
        ArticleRequest request = new ArticleRequest(
                "New Article", "Rich content body", "Jane Doe", "technology", true);

        // When / Then
        webTestClient.post()
                .uri("/api/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Article.class)
                .value(saved -> {
                    assertThat(saved.getId()).isNotNull().isNotEmpty();
                    assertThat(saved.getTitle()).isEqualTo("New Article");
                    assertThat(saved.getAuthor()).isEqualTo("Jane Doe");
                    assertThat(saved.getCategory()).isEqualTo("technology");
                    assertThat(saved.isPublished()).isTrue();
                    assertThat(saved.getCreatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("POST /api/articles returns 400 when the title is blank")
    void createArticle_returns400_whenTitleIsBlank() {
        // Given: invalid request with blank title
        ArticleRequest invalid = new ArticleRequest("", "Content", "Author", "tech", true);

        // When / Then
        webTestClient.post()
                .uri("/api/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/articles returns 400 when the content is blank")
    void createArticle_returns400_whenContentIsBlank() {
        ArticleRequest invalid = new ArticleRequest("Title", "", "Author", "tech", true);

        webTestClient.post()
                .uri("/api/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/articles returns 400 when the author is blank")
    void createArticle_returns400_whenAuthorIsBlank() {
        ArticleRequest invalid = new ArticleRequest("Title", "Content", "", "tech", true);

        webTestClient.post()
                .uri("/api/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/articles returns 400 when the category is blank")
    void createArticle_returns400_whenCategoryIsBlank() {
        ArticleRequest invalid = new ArticleRequest("Title", "Content", "Author", "", true);

        webTestClient.post()
                .uri("/api/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── PUT /api/articles/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/articles/{id} returns 200 with the updated article")
    void updateArticle_returns200WithUpdatedArticle() {
        // Given: an existing article
        Article created = articleService.create(
                new ArticleRequest("Old Title", "Old content", "Author", "misc", false)).block();

        ArticleRequest updateRequest = new ArticleRequest(
                "New Title", "New content", "Author", "technology", true);

        // When / Then
        webTestClient.put()
                .uri("/api/articles/{id}", created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Article.class)
                .value(updated -> {
                    assertThat(updated.getTitle()).isEqualTo("New Title");
                    assertThat(updated.getContent()).isEqualTo("New content");
                    assertThat(updated.getCategory()).isEqualTo("technology");
                    assertThat(updated.isPublished()).isTrue();
                    // The ID must be preserved
                    assertThat(updated.getId()).isEqualTo(created.getId());
                });
    }

    @Test
    @DisplayName("PUT /api/articles/{id} returns 404 when the article does not exist")
    void updateArticle_returns404_whenNotFound() {
        ArticleRequest request = new ArticleRequest("Title", "Content", "Author", "tech", true);

        webTestClient.put()
                .uri("/api/articles/507f1f77bcf86cd799439999")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── DELETE /api/articles/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/articles/{id} returns 204 and the article is no longer retrievable")
    void deleteArticle_returns204AndArticleGone() {
        // Given: a persisted article
        Article created = articleService.create(
                new ArticleRequest("Delete Me", "Body", "Author", "test", false)).block();

        // When: delete via HTTP
        webTestClient.delete()
                .uri("/api/articles/{id}", created.getId())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NO_CONTENT);

        // Then: a subsequent GET returns 404 (article is gone from MongoDB)
        webTestClient.get()
                .uri("/api/articles/{id}", created.getId())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("DELETE /api/articles/{id} returns 404 when the article does not exist")
    void deleteArticle_returns404_whenNotFound() {
        webTestClient.delete()
                .uri("/api/articles/507f1f77bcf86cd799439999")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── Service-layer MongoDB verification ────────────────────────────────────────

    @Test
    @DisplayName("Created article is persisted and retrievable from MongoDB")
    void createdArticle_isPersistedInMongoDB() {
        // Given: an article created via the service layer
        Article created = articleService.create(
                new ArticleRequest("Persisted Article", "Body", "Author", "test", true)).block();

        // When: retrieve directly from MongoDB via the service
        Article found = articleService.findById(created.getId()).block();

        // Then: the article exists with the correct data
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Persisted Article");
        assertThat(found.isPublished()).isTrue();
        assertThat(found.getId()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Updated article reflects new values in MongoDB")
    void updatedArticle_reflectsNewValuesInMongoDB() {
        // Given: an article with initial values
        Article created = articleService.create(
                new ArticleRequest("Before Update", "Old body", "Author", "test", false)).block();

        // When: update the article
        articleService.update(created.getId(),
                new ArticleRequest("After Update", "New body", "Author", "test", true)).block();

        // Then: re-fetching from MongoDB shows the new values
        Article updated = articleService.findById(created.getId()).block();
        assertThat(updated).isNotNull();
        assertThat(updated.getTitle()).isEqualTo("After Update");
        assertThat(updated.getContent()).isEqualTo("New body");
        assertThat(updated.isPublished()).isTrue();
    }

    @Test
    @DisplayName("Deleted article is no longer present in MongoDB")
    void deletedArticle_isRemovedFromMongoDB() {
        // Given: an article in MongoDB
        Article created = articleService.create(
                new ArticleRequest("Temp", "Body", "Author", "test", false)).block();
        String id = created.getId();

        // When: delete the article
        Boolean deleted = articleService.deleteById(id).block();

        // Then: the article is gone
        assertThat(deleted).isTrue();
        assertThat(articleService.findById(id).block()).isNull();
        assertThat(articleRepository.count().block()).isZero();
    }
}
