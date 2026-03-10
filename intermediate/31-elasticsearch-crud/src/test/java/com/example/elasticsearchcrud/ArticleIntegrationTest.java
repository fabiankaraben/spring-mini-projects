package com.example.elasticsearchcrud;

import com.example.elasticsearchcrud.domain.Article;
import com.example.elasticsearchcrud.dto.ArticleRequest;
import com.example.elasticsearchcrud.repository.ArticleRepository;
import com.example.elasticsearchcrud.service.ArticleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the Elasticsearch CRUD API.
 *
 * <p>This test class verifies end-to-end behaviour from the HTTP layer through
 * the service and repository layers down to a real Elasticsearch instance managed
 * by Testcontainers. Key aspects:
 *
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded
 *       servlet container on a random port, so the full Spring MVC filter chain,
 *       serialisation stack, and exception handlers are active — identical to
 *       production runtime.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up an Elasticsearch Docker
 *       container for the duration of the test class. The container is shared across
 *       all test methods to avoid the overhead of restarting Elasticsearch for each
 *       individual test.</li>
 *   <li>{@link DynamicPropertySource} overrides the Elasticsearch URI in the Spring
 *       {@code Environment} before the application context is created, so Spring Boot
 *       connects to the Testcontainers-managed Elasticsearch instead of any locally
 *       installed instance.</li>
 *   <li>The {@code "test"} profile activates {@code application-test.yml} which
 *       reduces logging noise during test runs.</li>
 * </ul>
 *
 * <p><strong>Important Elasticsearch-specific setup:</strong>
 * The official Elasticsearch 8.x Docker image enables security (TLS + authentication)
 * by default. The Testcontainers {@link ElasticsearchContainer} disables security in
 * its default configuration so tests can connect without certificates. The container
 * URL includes the password when security is enabled; Testcontainers handles this.
 *
 * <p><strong>Index lifecycle:</strong>
 * Spring Data Elasticsearch does not auto-create the index when {@code createIndex = false}
 * is set on the {@link org.springframework.data.elasticsearch.annotations.Document}. We
 * explicitly create the index in {@link #setUp()} using the repository's underlying
 * operations, and delete all documents before each test method to ensure test isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Article integration tests (Elasticsearch + REST API)")
class ArticleIntegrationTest {

    // ── Testcontainers Elasticsearch container ────────────────────────────────────

    /**
     * An Elasticsearch container shared by all tests in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses a single container
     * instance for the entire test class lifecycle, avoiding the significant overhead
     * of starting/stopping Elasticsearch for each test method.
     *
     * <p>We use the official Elasticsearch image version that matches Spring Boot 3.4.x's
     * supported Elasticsearch version (8.x). The
     * {@link ElasticsearchContainer} Testcontainers module sets the necessary environment
     * variables to disable TLS and authentication for testing convenience.
     */
    @Container
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.3"))
                    .withEnv("xpack.security.enabled", "false");

    /**
     * Register the container's dynamic HTTP URL into the Spring {@link org.springframework.core.env.Environment}
     * before the application context is created.
     *
     * <p>Testcontainers assigns a random host port to the container, so we use
     * {@code getHttpHostAddress()} to get the full host:port string and construct
     * the URI that Spring Boot's Elasticsearch auto-configuration will use.
     *
     * @param registry the property registry read by Spring Boot before context startup
     */
    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        // Override the Elasticsearch URI to point at the Testcontainers container.
        // No username/password needed because we disabled xpack security above.
        registry.add("spring.elasticsearch.uris",
                () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
        registry.add("spring.elasticsearch.username", () -> "");
        registry.add("spring.elasticsearch.password", () -> "");
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /** The random port chosen by Spring Boot for the embedded servlet container. */
    @LocalServerPort
    int port;

    /**
     * {@link TestRestTemplate} is designed for integration tests:
     * <ul>
     *   <li>Does NOT throw exceptions on 4xx/5xx (returns the ResponseEntity).</li>
     *   <li>Follows HTTP redirects automatically.</li>
     * </ul>
     */
    @Autowired
    TestRestTemplate restTemplate;

    /** Direct access to the service layer for test data setup. */
    @Autowired
    ArticleService articleService;

    /** Direct access to the repository for cleanup between tests. */
    @Autowired
    ArticleRepository articleRepository;

    // ── Setup ─────────────────────────────────────────────────────────────────────

    /**
     * Delete all Elasticsearch documents before each test method.
     *
     * <p>Without this, documents created in one test would be visible to subsequent
     * tests, causing non-deterministic failures (e.g. "findAll" returning more articles
     * than expected).
     *
     * <p>We also ensure the index exists by saving and deleting a dummy document,
     * which forces Spring Data Elasticsearch to create the index mapping if needed.
     */
    @BeforeEach
    void setUp() {
        articleRepository.deleteAll();
    }

    // ── GET /api/articles ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/articles returns 200 with empty array when index is empty")
    void getAllArticles_returns200WithEmptyArray_whenIndexIsEmpty() {
        // When: list all articles (index was cleared in setUp)
        ResponseEntity<Article[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/articles",
                Article[].class
        );

        // Then: 200 OK with an empty array
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("GET /api/articles returns 200 with all articles when documents exist")
    void getAllArticles_returns200WithAllArticles() {
        // Given: two articles in the index
        articleService.create(new ArticleRequest("Spring Boot Intro", "Content A", "Author A", "technology", 10));
        articleService.create(new ArticleRequest("Docker Guide", "Content B", "Author B", "devops", 20));

        // When
        ResponseEntity<Article[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/articles",
                Article[].class
        );

        // Then: 200 OK with both articles
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(2);
    }

    // ── GET /api/articles/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/articles/{id} returns 200 with the article when found")
    void getArticleById_returns200WithArticle_whenFound() {
        // Given: an article in the index
        Article created = articleService.create(
                new ArticleRequest("Test Article", "Some content", "Test Author", "testing", 5));

        // When
        ResponseEntity<Article> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/articles/" + created.getId(),
                Article.class
        );

        // Then: 200 OK with matching data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Test Article");
        assertThat(response.getBody().getAuthor()).isEqualTo("Test Author");
    }

    @Test
    @DisplayName("GET /api/articles/{id} returns 404 when the article does not exist")
    void getArticleById_returns404_whenNotFound() {
        // When: request an article with a non-existent ID
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/articles/nonexistent-id-xyz",
                String.class
        );

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/articles/author/{author} ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/articles/author/{author} returns articles by that author")
    void getArticlesByAuthor_returnsMatchingArticles() {
        // Given: two articles by "Alice", one by "Bob"
        articleService.create(new ArticleRequest("Alice Article 1", "Content", "Alice", "tech", 10));
        articleService.create(new ArticleRequest("Alice Article 2", "Content", "Alice", "tech", 20));
        articleService.create(new ArticleRequest("Bob Article", "Content", "Bob", "devops", 5));

        // When: request Alice's articles
        ResponseEntity<Article[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/articles/author/Alice",
                Article[].class
        );

        // Then: only Alice's two articles are returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(2);
        assertThat(response.getBody()).allMatch(a -> "Alice".equals(a.getAuthor()));
    }

    // ── GET /api/articles/category/{category} ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/articles/category/{category} returns articles in that category")
    void getArticlesByCategory_returnsMatchingArticles() {
        // Given: one technology and one devops article
        articleService.create(new ArticleRequest("Spring Guide", "Content", "Author", "technology", 10));
        articleService.create(new ArticleRequest("K8s Guide", "Content", "Author", "devops", 30));

        // When
        ResponseEntity<Article[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/articles/category/technology",
                Article[].class
        );

        // Then: only the technology article is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody()[0].getCategory()).isEqualTo("technology");
    }

    // ── GET /api/articles/popular?threshold=... ───────────────────────────────────

    @Test
    @DisplayName("GET /api/articles/popular?threshold=... returns articles above view threshold")
    void getPopularArticles_returnsArticlesAboveThreshold() {
        // Given: a popular and a non-popular article
        articleService.create(new ArticleRequest("Viral Post", "Content", "Author", "misc", 500));
        articleService.create(new ArticleRequest("Obscure Post", "Content", "Author", "misc", 3));

        // When: threshold = 100
        ResponseEntity<Article[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/articles/popular?threshold=100",
                Article[].class
        );

        // Then: only the popular article is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody()[0].getTitle()).isEqualTo("Viral Post");
    }

    // ── POST /api/articles ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/articles returns 201 with the created article including generated ID")
    void createArticle_returns201WithArticle() {
        // Given: a valid article request
        ArticleRequest request = new ArticleRequest(
                "New Article", "Article body text", "John Doe", "science", 0);

        // When
        ResponseEntity<Article> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/articles",
                request,
                Article.class
        );

        // Then: 201 Created with an Elasticsearch-assigned ID
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull().isNotEmpty();
        assertThat(response.getBody().getTitle()).isEqualTo("New Article");
        assertThat(response.getBody().getAuthor()).isEqualTo("John Doe");
        assertThat(response.getBody().getViewCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("POST /api/articles returns 400 when the title is blank")
    void createArticle_returns400_whenTitleIsBlank() {
        // Given: invalid request with blank title
        ArticleRequest invalid = new ArticleRequest("", "content", "author", "cat", 0);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/articles",
                invalid,
                String.class
        );

        // Then: 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/articles returns 400 when content is blank")
    void createArticle_returns400_whenContentIsBlank() {
        // Given: invalid request with blank content
        ArticleRequest invalid = new ArticleRequest("Title", "", "author", "cat", 0);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/articles",
                invalid,
                String.class
        );

        // Then: 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/articles returns 400 when view count is negative")
    void createArticle_returns400_whenViewCountIsNegative() {
        // Given: invalid request with negative view count
        ArticleRequest invalid = new ArticleRequest("Title", "Content", "Author", "cat", -1);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/articles",
                invalid,
                String.class
        );

        // Then: 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── PUT /api/articles/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/articles/{id} returns 200 with the updated article")
    void updateArticle_returns200WithUpdatedArticle() {
        // Given: an existing article
        Article created = articleService.create(
                new ArticleRequest("Original Title", "Original content", "Author", "misc", 10));

        ArticleRequest updateRequest = new ArticleRequest(
                "Updated Title", "Updated content", "Author", "misc", 25);

        // When: PUT the update
        ResponseEntity<Article> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/articles/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                Article.class
        );

        // Then: 200 OK with updated values
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Updated Title");
        assertThat(response.getBody().getContent()).isEqualTo("Updated content");
        assertThat(response.getBody().getViewCount()).isEqualTo(25);
        // The ID must be preserved
        assertThat(response.getBody().getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("PUT /api/articles/{id} returns 404 when the article does not exist")
    void updateArticle_returns404_whenNotFound() {
        // When: attempt to update a non-existent article
        ArticleRequest request = new ArticleRequest("x", "x", "x", "x", 0);
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/articles/nonexistent-id-xyz",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                String.class
        );

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE /api/articles/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/articles/{id} returns 204 and article is no longer retrievable")
    void deleteArticle_returns204AndArticleGone() {
        // Given: an article to delete
        Article created = articleService.create(
                new ArticleRequest("To Delete", "Content", "Author", "misc", 1));

        // When: delete via HTTP
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/articles/" + created.getId(),
                HttpMethod.DELETE,
                null,
                Void.class
        );

        // Then: 204 No Content
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // And: a subsequent GET returns 404 (article is gone from the index)
        ResponseEntity<String> getResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/articles/" + created.getId(),
                String.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /api/articles/{id} returns 404 when the article does not exist")
    void deleteArticle_returns404_whenNotFound() {
        // When: delete a non-existent article
        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/articles/nonexistent-id-xyz",
                HttpMethod.DELETE,
                null,
                Void.class
        );

        // Then: 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Service-layer Elasticsearch verification ──────────────────────────────────

    @Test
    @DisplayName("Created article is persisted and retrievable from Elasticsearch")
    void createdArticle_isPersistedInElasticsearch() {
        // Given: an article created via the service layer
        Article created = articleService.create(
                new ArticleRequest("Persistent Article", "Verify in ES", "Author", "test", 7));

        // When: retrieve directly from the service
        Optional<Article> found = articleService.findById(created.getId());

        // Then: the article is present with correct data
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Persistent Article");
        assertThat(found.get().getViewCount()).isEqualTo(7);
        assertThat(found.get().getId()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Updated article reflects new values in Elasticsearch")
    void updatedArticle_reflectsNewValuesInElasticsearch() {
        // Given: an article with initial values
        Article created = articleService.create(
                new ArticleRequest("Before Update", "Old content", "Author", "test", 10));

        // When: update the article
        articleService.update(created.getId(),
                new ArticleRequest("After Update", "New content", "Author", "test", 50));

        // Then: re-fetching shows the new values
        Optional<Article> updated = articleService.findById(created.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().getTitle()).isEqualTo("After Update");
        assertThat(updated.get().getContent()).isEqualTo("New content");
        assertThat(updated.get().getViewCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("Deleted article is no longer present in Elasticsearch")
    void deletedArticle_isRemovedFromElasticsearch() {
        // Given: an article in the index
        Article created = articleService.create(
                new ArticleRequest("Delete Me", "Content", "Author", "test", 1));
        String id = created.getId();

        // When: delete the article
        boolean deleted = articleService.deleteById(id);

        // Then: the article is gone from Elasticsearch
        assertThat(deleted).isTrue();
        assertThat(articleService.findById(id)).isEmpty();
        assertThat(articleRepository.count()).isZero();
    }

    @Test
    @DisplayName("findByCategory returns only articles in the specified category")
    void findByCategory_returnsOnlyCategoryArticles() {
        // Given: articles in different categories
        articleService.create(new ArticleRequest("ES Article 1", "Content", "Author", "elasticsearch", 10));
        articleService.create(new ArticleRequest("ES Article 2", "Content", "Author", "elasticsearch", 20));
        articleService.create(new ArticleRequest("Java Article", "Content", "Author", "java", 5));

        // When
        List<Article> esArticles = articleService.findByCategory("elasticsearch");

        // Then: only the two elasticsearch articles are returned
        assertThat(esArticles).hasSize(2);
        assertThat(esArticles).allMatch(a -> "elasticsearch".equals(a.getCategory()));
    }
}
