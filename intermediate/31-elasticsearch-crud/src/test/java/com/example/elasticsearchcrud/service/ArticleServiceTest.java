package com.example.elasticsearchcrud.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.example.elasticsearchcrud.domain.Article;
import com.example.elasticsearchcrud.dto.ArticleRequest;
import com.example.elasticsearchcrud.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ArticleService}.
 *
 * <p>These tests exercise the service's business logic in complete isolation:
 * <ul>
 *   <li>{@link ArticleRepository} is replaced with a Mockito mock — no real
 *       Elasticsearch connection is needed. Tests run in milliseconds.</li>
 *   <li>{@link ElasticsearchClient} is also mocked, allowing us to control
 *       what the low-level client returns for full-text search calls.</li>
 *   <li>No Spring context is loaded — {@link ExtendWith}({@link MockitoExtension}.class)
 *       initialises Mockito annotations only, keeping startup time near zero.</li>
 *   <li>Each test follows the Given / When / Then pattern for clarity.</li>
 * </ul>
 *
 * <p>Integration tests (see {@link com.example.elasticsearchcrud.ArticleIntegrationTest})
 * cover the full stack with a real Elasticsearch container via Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleService unit tests")
class ArticleServiceTest {

    /**
     * Mock for the Spring Data Elasticsearch repository.
     * All repository calls return pre-configured stubs via {@code when(...).thenReturn(...)}.
     */
    @Mock
    private ArticleRepository articleRepository;

    /**
     * Mock for the low-level Elasticsearch Java API client.
     * Used to stub the {@code search()} call in {@link ArticleService#fullTextSearch(String)}.
     */
    @Mock
    private ElasticsearchClient elasticsearchClient;

    /**
     * The class under test.
     * Mockito creates the instance and injects both mocks via constructor injection.
     */
    @InjectMocks
    private ArticleService articleService;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    /** A pre-built Article returned by the mock repository in various tests. */
    private Article sampleArticle;

    /** A request DTO that would arrive in an HTTP POST/PUT request body. */
    private ArticleRequest sampleRequest;

    @BeforeEach
    void setUp() {
        // Build a sample domain object
        sampleArticle = new Article(
                "Understanding Spring Boot",
                "A deep dive into Spring Boot auto-configuration",
                "Alice Johnson",
                "technology",
                120
        );
        sampleArticle.setId("es-doc-id-001");

        // Build a corresponding request DTO
        sampleRequest = new ArticleRequest(
                "Understanding Spring Boot",
                "A deep dive into Spring Boot auto-configuration",
                "Alice Johnson",
                "technology",
                120
        );
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns all articles from the repository")
    void findAll_returnsAllArticles() {
        // Given: the repository holds two articles
        Article second = new Article("Docker Basics", "Intro to Docker", "Bob Smith", "devops", 50);
        when(articleRepository.findAll()).thenReturn(List.of(sampleArticle, second));

        // When
        List<Article> results = articleService.findAll();

        // Then: both articles are returned
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Article::getTitle)
                .containsExactlyInAnyOrder("Understanding Spring Boot", "Docker Basics");
        verify(articleRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll returns empty list when the index is empty")
    void findAll_returnsEmptyList_whenIndexIsEmpty() {
        // Given: no documents in the index
        when(articleRepository.findAll()).thenReturn(List.of());

        // When
        List<Article> results = articleService.findAll();

        // Then: an empty list is returned (not null)
        assertThat(results).isNotNull().isEmpty();
        verify(articleRepository, times(1)).findAll();
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns the article wrapped in Optional when it exists")
    void findById_returnsArticle_whenExists() {
        // Given
        when(articleRepository.findById("es-doc-id-001"))
                .thenReturn(Optional.of(sampleArticle));

        // When
        Optional<Article> result = articleService.findById("es-doc-id-001");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("es-doc-id-001");
        assertThat(result.get().getTitle()).isEqualTo("Understanding Spring Boot");
        verify(articleRepository, times(1)).findById("es-doc-id-001");
    }

    @Test
    @DisplayName("findById returns empty Optional when the article does not exist")
    void findById_returnsEmpty_whenNotFound() {
        // Given
        when(articleRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When
        Optional<Article> result = articleService.findById("nonexistent");

        // Then
        assertThat(result).isEmpty();
        verify(articleRepository, times(1)).findById("nonexistent");
    }

    // ── findByAuthor ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByAuthor returns articles by the given author")
    void findByAuthor_returnsMatchingArticles() {
        // Given: two articles by the same author
        Article second = new Article("ES Tips", "Elasticsearch tips", "Alice Johnson", "technology", 30);
        when(articleRepository.findByAuthor("Alice Johnson"))
                .thenReturn(List.of(sampleArticle, second));

        // When
        List<Article> results = articleService.findByAuthor("Alice Johnson");

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(a -> "Alice Johnson".equals(a.getAuthor()));
        verify(articleRepository, times(1)).findByAuthor("Alice Johnson");
    }

    @Test
    @DisplayName("findByAuthor returns empty list when author has no articles")
    void findByAuthor_returnsEmpty_whenNoMatch() {
        // Given
        when(articleRepository.findByAuthor("Unknown Author")).thenReturn(List.of());

        // When
        List<Article> results = articleService.findByAuthor("Unknown Author");

        // Then
        assertThat(results).isEmpty();
    }

    // ── findByCategory ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByCategory returns articles in the given category")
    void findByCategory_returnsMatchingArticles() {
        // Given
        when(articleRepository.findByCategory("technology"))
                .thenReturn(List.of(sampleArticle));

        // When
        List<Article> results = articleService.findByCategory("technology");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCategory()).isEqualTo("technology");
        verify(articleRepository, times(1)).findByCategory("technology");
    }

    // ── findByViewCountGreaterThan ─────────────────────────────────────────────────

    @Test
    @DisplayName("findByViewCountGreaterThan returns articles above the threshold")
    void findByViewCountGreaterThan_returnsPopularArticles() {
        // Given: sampleArticle has viewCount=120 which is > 100
        when(articleRepository.findByViewCountGreaterThan(100))
                .thenReturn(List.of(sampleArticle));

        // When
        List<Article> results = articleService.findByViewCountGreaterThan(100);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getViewCount()).isGreaterThan(100);
        verify(articleRepository, times(1)).findByViewCountGreaterThan(100);
    }

    @Test
    @DisplayName("findByViewCountGreaterThan returns empty list when no article exceeds threshold")
    void findByViewCountGreaterThan_returnsEmpty_whenNoMatch() {
        // Given: threshold higher than any article's view count
        when(articleRepository.findByViewCountGreaterThan(999)).thenReturn(List.of());

        // When
        List<Article> results = articleService.findByViewCountGreaterThan(999);

        // Then
        assertThat(results).isEmpty();
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create maps the DTO to an Article and persists it with an assigned ID")
    void create_persistsArticleAndReturnsWithId() {
        // Given: the repository assigns an ID when saving
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> {
            Article a = invocation.getArgument(0);
            a.setId("generated-es-id"); // simulate Elasticsearch-assigned UUID
            return a;
        });

        // When
        Article created = articleService.create(sampleRequest);

        // Then: the created article has the correct data and an assigned ID
        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo("generated-es-id");
        assertThat(created.getTitle()).isEqualTo(sampleRequest.title());
        assertThat(created.getContent()).isEqualTo(sampleRequest.content());
        assertThat(created.getAuthor()).isEqualTo(sampleRequest.author());
        assertThat(created.getCategory()).isEqualTo(sampleRequest.category());
        assertThat(created.getViewCount()).isEqualTo(sampleRequest.viewCount());
        // Timestamps must have been set by the Article constructor
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        verify(articleRepository, times(1)).save(any(Article.class));
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update returns updated article wrapped in Optional when the article exists")
    void update_returnsUpdatedArticle_whenExists() {
        // Given: the article exists
        when(articleRepository.findById("es-doc-id-001"))
                .thenReturn(Optional.of(sampleArticle));
        when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));

        ArticleRequest updateRequest = new ArticleRequest(
                "Updated Title",
                "Updated content about Spring Boot",
                "Alice Johnson",
                "technology",
                150
        );

        // When
        Optional<Article> result = articleService.update("es-doc-id-001", updateRequest);

        // Then: the updated article is returned with the new values
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Updated Title");
        assertThat(result.get().getContent()).isEqualTo("Updated content about Spring Boot");
        assertThat(result.get().getViewCount()).isEqualTo(150);
        // updatedAt should have been refreshed
        assertThat(result.get().getUpdatedAt()).isNotNull();
        verify(articleRepository, times(1)).findById("es-doc-id-001");
        verify(articleRepository, times(1)).save(any(Article.class));
    }

    @Test
    @DisplayName("update returns empty Optional when the article does not exist")
    void update_returnsEmpty_whenArticleNotFound() {
        // Given: no article with the given ID
        when(articleRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When
        Optional<Article> result = articleService.update("nonexistent", sampleRequest);

        // Then: empty Optional and save is never called
        assertThat(result).isEmpty();
        verify(articleRepository, times(1)).findById("nonexistent");
        verify(articleRepository, never()).save(any(Article.class));
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById returns true when the article exists and is deleted")
    void deleteById_returnsTrue_whenArticleExists() {
        // Given
        when(articleRepository.existsById("es-doc-id-001")).thenReturn(true);

        // When
        boolean result = articleService.deleteById("es-doc-id-001");

        // Then
        assertThat(result).isTrue();
        verify(articleRepository, times(1)).existsById("es-doc-id-001");
        verify(articleRepository, times(1)).deleteById("es-doc-id-001");
    }

    @Test
    @DisplayName("deleteById returns false when the article does not exist")
    void deleteById_returnsFalse_whenArticleNotFound() {
        // Given
        when(articleRepository.existsById("nonexistent")).thenReturn(false);

        // When
        boolean result = articleService.deleteById("nonexistent");

        // Then: false is returned and deleteById is never called
        assertThat(result).isFalse();
        verify(articleRepository, times(1)).existsById("nonexistent");
        verify(articleRepository, never()).deleteById(anyString());
    }

    // ── fullTextSearch ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fullTextSearch returns articles from the ElasticsearchClient response")
    @SuppressWarnings("unchecked")
    void fullTextSearch_returnsMatchingArticles() throws IOException {
        // Given: build a mock SearchResponse with one hit containing sampleArticle.
        // The Elasticsearch Java API client uses a builder pattern; we need to construct
        // a realistic-looking SearchResponse to stub the client call.

        // Build a TotalHits object indicating 1 result
        TotalHits totalHits = TotalHits.of(t -> t
                .value(1)
                .relation(TotalHitsRelation.Eq));

        // Build a Hit<Article> containing the sampleArticle as the source document
        co.elastic.clients.elasticsearch.core.search.Hit<Article> hit =
                co.elastic.clients.elasticsearch.core.search.Hit.of(h -> h
                        .index("articles")
                        .id("es-doc-id-001")
                        .source(sampleArticle));

        // Build the HitsMetadata wrapping the hit list and total count
        HitsMetadata<Article> hitsMetadata = HitsMetadata.of(hm -> hm
                .hits(List.of(hit))
                .total(totalHits));

        // Build the full SearchResponse
        SearchResponse<Article> searchResponse = SearchResponse.of(sr -> sr
                .hits(hitsMetadata)
                .took(5)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0)));

        // Stub the ElasticsearchClient.search() to return our fake response
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Article.class)))
                .thenReturn(searchResponse);

        // When
        List<Article> results = articleService.fullTextSearch("Spring Boot");

        // Then: the result list contains the article from the stubbed response
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Understanding Spring Boot");
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Article.class));
    }

    @Test
    @DisplayName("fullTextSearch wraps IOException in RuntimeException")
    @SuppressWarnings("unchecked")
    void fullTextSearch_wrapsIOException() throws IOException {
        // Given: the client throws an IOException (e.g. network failure)
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Article.class)))
                .thenThrow(new IOException("Connection refused"));

        // When / Then: the service wraps it in a RuntimeException
        assertThatThrownBy(() -> articleService.fullTextSearch("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Elasticsearch query failed");
    }
}
