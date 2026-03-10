package com.example.reactivewebfluxapi.service;

import com.example.reactivewebfluxapi.domain.Article;
import com.example.reactivewebfluxapi.dto.ArticleRequest;
import com.example.reactivewebfluxapi.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ArticleService}.
 *
 * <p>These tests verify the service's business logic in pure isolation:
 * <ul>
 *   <li>The {@link ArticleRepository} is replaced with a Mockito mock, so no real
 *       MongoDB connection is needed. Tests run in milliseconds without Docker.</li>
 *   <li>No Spring context is loaded — {@link ExtendWith}({@link MockitoExtension}.class)
 *       initialises Mockito annotations only, keeping startup time near zero.</li>
 *   <li>{@link StepVerifier} (from {@code reactor-test}) is used to test reactive
 *       pipelines step-by-step in a synchronous, deterministic way. It subscribes
 *       to a {@link Mono} or {@link Flux}, then asserts on each emitted item,
 *       completion, or error signal.</li>
 *   <li>Each test follows the Given / When / Then (Arrange / Act / Assert) pattern
 *       to make intent and expectations explicit.</li>
 * </ul>
 *
 * <p><strong>Why StepVerifier?</strong><br>
 * Reactive types are lazy — nothing executes until subscribed. Simply calling
 * {@code articleService.findAll()} returns a cold {@link Flux} but does NOT trigger
 * any database calls. {@code StepVerifier.create(...).expectNext(...).verifyComplete()}
 * subscribes and blocks the test thread until the publisher completes, making
 * reactive assertions feel like ordinary JUnit assertions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleService unit tests")
class ArticleServiceTest {

    /**
     * Mockito mock of the repository — no real MongoDB involved.
     * All interactions are simulated via {@code when(...).thenReturn(...)}.
     */
    @Mock
    private ArticleRepository articleRepository;

    /**
     * The class under test.
     * {@code @InjectMocks} creates an {@link ArticleService} instance and injects
     * the {@code @Mock} fields into it via constructor injection.
     */
    @InjectMocks
    private ArticleService articleService;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    /** A pre-built domain object returned by the mock repository. */
    private Article sampleArticle;

    /** A DTO representing an incoming HTTP POST/PUT request body. */
    private ArticleRequest sampleRequest;

    @BeforeEach
    void setUp() {
        // Build a sample domain entity that the mock repository will return
        sampleArticle = new Article(
                "Spring WebFlux Explained",
                "A deep dive into reactive programming with Project Reactor",
                "Jane Doe",
                "technology",
                true
        );
        sampleArticle.setId("507f1f77bcf86cd799439011");

        // Build the corresponding request DTO
        sampleRequest = new ArticleRequest(
                "Spring WebFlux Explained",
                "A deep dive into reactive programming with Project Reactor",
                "Jane Doe",
                "technology",
                true
        );
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll emits all articles from the repository")
    void findAll_emitsAllArticles() {
        // Given: the mock repository returns two articles as a Flux
        Article second = new Article("Reactor Patterns", "Reactive patterns guide", "Bob Smith", "technology", false);
        when(articleRepository.findAll()).thenReturn(Flux.just(sampleArticle, second));

        // When / Then: StepVerifier subscribes and asserts both items are emitted in order,
        //              then the Flux completes without error.
        StepVerifier.create(articleService.findAll())
                .expectNext(sampleArticle)   // first item emitted
                .expectNext(second)          // second item emitted
                .verifyComplete();           // Flux completed with no error

        verify(articleRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll emits nothing and completes when the collection is empty")
    void findAll_completesEmpty_whenNoArticles() {
        // Given: the repository returns an empty Flux
        when(articleRepository.findAll()).thenReturn(Flux.empty());

        // When / Then: the Flux completes immediately with no items
        StepVerifier.create(articleService.findAll())
                .verifyComplete(); // no items, just completion signal
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById emits the article when it exists")
    void findById_emitsArticle_whenFound() {
        // Given: the repository finds the article
        when(articleRepository.findById("507f1f77bcf86cd799439011"))
                .thenReturn(Mono.just(sampleArticle));

        // When / Then: the Mono emits the article then completes
        StepVerifier.create(articleService.findById("507f1f77bcf86cd799439011"))
                .expectNextMatches(a -> a.getId().equals("507f1f77bcf86cd799439011")
                        && a.getTitle().equals("Spring WebFlux Explained"))
                .verifyComplete();

        verify(articleRepository, times(1)).findById("507f1f77bcf86cd799439011");
    }

    @Test
    @DisplayName("findById completes empty when the article does not exist")
    void findById_completesEmpty_whenNotFound() {
        // Given: no article with this ID
        when(articleRepository.findById("nonexistent")).thenReturn(Mono.empty());

        // When / Then: the Mono is empty (the controller will map this to HTTP 404)
        StepVerifier.create(articleService.findById("nonexistent"))
                .verifyComplete(); // empty completion — no items
    }

    // ── findByCategory ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByCategory emits articles matching the category")
    void findByCategory_emitsMatchingArticles() {
        // Given: the repository returns one technology article
        when(articleRepository.findByCategory("technology"))
                .thenReturn(Flux.just(sampleArticle));

        // When / Then
        StepVerifier.create(articleService.findByCategory("technology"))
                .expectNextMatches(a -> "technology".equals(a.getCategory()))
                .verifyComplete();

        verify(articleRepository, times(1)).findByCategory("technology");
    }

    @Test
    @DisplayName("findByCategory completes empty when no articles match")
    void findByCategory_completesEmpty_whenNoMatch() {
        // Given: no articles in "sports" category
        when(articleRepository.findByCategory("sports")).thenReturn(Flux.empty());

        StepVerifier.create(articleService.findByCategory("sports"))
                .verifyComplete();
    }

    // ── searchByTitle ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("searchByTitle emits articles whose titles contain the keyword")
    void searchByTitle_emitsMatchingArticles() {
        // Given: repository returns an article matching "WebFlux"
        when(articleRepository.findByTitleContainingIgnoreCase("WebFlux"))
                .thenReturn(Flux.just(sampleArticle));

        // When / Then
        StepVerifier.create(articleService.searchByTitle("WebFlux"))
                .expectNextMatches(a -> a.getTitle().contains("WebFlux"))
                .verifyComplete();

        verify(articleRepository, times(1)).findByTitleContainingIgnoreCase("WebFlux");
    }

    // ── findPublished ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findPublished emits only published articles")
    void findPublished_emitsPublishedArticles() {
        // Given: the sample article is published
        when(articleRepository.findByPublished(true)).thenReturn(Flux.just(sampleArticle));

        // When / Then
        StepVerifier.create(articleService.findPublished())
                .expectNextMatches(Article::isPublished) // verify it is actually published
                .verifyComplete();

        verify(articleRepository, times(1)).findByPublished(true);
    }

    // ── findByAuthor ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByAuthor emits articles written by the given author")
    void findByAuthor_emitsArticlesByAuthor() {
        // Given
        when(articleRepository.findByAuthor("Jane Doe")).thenReturn(Flux.just(sampleArticle));

        // When / Then
        StepVerifier.create(articleService.findByAuthor("Jane Doe"))
                .expectNextMatches(a -> "Jane Doe".equals(a.getAuthor()))
                .verifyComplete();

        verify(articleRepository, times(1)).findByAuthor("Jane Doe");
    }

    // ── countByAuthor ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("countByAuthor emits the number of articles by the given author")
    void countByAuthor_emitsCount() {
        // Given: Jane Doe has written 3 articles
        when(articleRepository.countByAuthor("Jane Doe")).thenReturn(Mono.just(3L));

        // When / Then: the Mono emits exactly 3
        StepVerifier.create(articleService.countByAuthor("Jane Doe"))
                .expectNext(3L)
                .verifyComplete();

        verify(articleRepository, times(1)).countByAuthor("Jane Doe");
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create maps the request DTO to an Article and persists it")
    void create_persistsArticleAndEmitsSaved() {
        // Given: the mock repository simulates MongoDB assigning an ObjectId on save
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> {
            Article a = invocation.getArgument(0);
            a.setId("507f1f77bcf86cd799439011"); // simulate MongoDB-generated ObjectId
            return Mono.just(a);
        });

        // When / Then: the Mono emits the saved article with a generated id
        StepVerifier.create(articleService.create(sampleRequest))
                .expectNextMatches(saved -> {
                    // Verify the DTO fields were correctly mapped to the entity
                    return "507f1f77bcf86cd799439011".equals(saved.getId())
                            && "Spring WebFlux Explained".equals(saved.getTitle())
                            && "Jane Doe".equals(saved.getAuthor())
                            && "technology".equals(saved.getCategory())
                            && saved.isPublished()
                            && saved.getCreatedAt() != null
                            && saved.getUpdatedAt() != null;
                })
                .verifyComplete();

        verify(articleRepository, times(1)).save(any(Article.class));
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update emits the updated article when it exists")
    void update_emitsUpdatedArticle_whenFound() {
        // Given: the article is found
        when(articleRepository.findById("507f1f77bcf86cd799439011"))
                .thenReturn(Mono.just(sampleArticle));
        // And save returns the mutated entity
        when(articleRepository.save(any(Article.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // New values to apply
        ArticleRequest updateRequest = new ArticleRequest(
                "Updated Title",
                "Updated Content",
                "Jane Doe",
                "science",
                false
        );

        // When / Then
        StepVerifier.create(articleService.update("507f1f77bcf86cd799439011", updateRequest))
                .expectNextMatches(updated ->
                        "Updated Title".equals(updated.getTitle())
                        && "Updated Content".equals(updated.getContent())
                        && "science".equals(updated.getCategory())
                        && !updated.isPublished()
                        && updated.getUpdatedAt() != null)
                .verifyComplete();

        verify(articleRepository, times(1)).findById("507f1f77bcf86cd799439011");
        verify(articleRepository, times(1)).save(any(Article.class));
    }

    @Test
    @DisplayName("update completes empty when the article does not exist")
    void update_completesEmpty_whenNotFound() {
        // Given: no article with this ID
        when(articleRepository.findById("nonexistent")).thenReturn(Mono.empty());

        // When / Then: the service returns an empty Mono (controller maps to 404)
        StepVerifier.create(articleService.update("nonexistent", sampleRequest))
                .verifyComplete(); // empty — nothing to update

        verify(articleRepository, times(1)).findById("nonexistent");
        // save() must NEVER be called when the article was not found
        verify(articleRepository, never()).save(any(Article.class));
    }

    @Test
    @DisplayName("update refreshes the updatedAt timestamp on every call")
    void update_refreshesUpdatedAt() {
        // Given: an article with an old updatedAt
        Instant oldUpdatedAt = Instant.parse("2024-01-01T00:00:00Z");
        sampleArticle.setUpdatedAt(oldUpdatedAt);

        when(articleRepository.findById("507f1f77bcf86cd799439011"))
                .thenReturn(Mono.just(sampleArticle));
        when(articleRepository.save(any(Article.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // When / Then: updatedAt should be later than the old value
        StepVerifier.create(articleService.update("507f1f77bcf86cd799439011", sampleRequest))
                .expectNextMatches(updated -> updated.getUpdatedAt().isAfter(oldUpdatedAt))
                .verifyComplete();
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById emits true when the article exists and is deleted")
    void deleteById_emitsTrue_whenArticleExists() {
        // Given: the article is found and deleteById returns Mono<Void>
        when(articleRepository.findById("507f1f77bcf86cd799439011"))
                .thenReturn(Mono.just(sampleArticle));
        when(articleRepository.deleteById("507f1f77bcf86cd799439011"))
                .thenReturn(Mono.empty());

        // When / Then: the Mono emits true (article was deleted)
        StepVerifier.create(articleService.deleteById("507f1f77bcf86cd799439011"))
                .expectNext(true)
                .verifyComplete();

        verify(articleRepository, times(1)).findById("507f1f77bcf86cd799439011");
        verify(articleRepository, times(1)).deleteById("507f1f77bcf86cd799439011");
    }

    @Test
    @DisplayName("deleteById emits false when the article does not exist")
    void deleteById_emitsFalse_whenNotFound() {
        // Given: no article with this ID
        when(articleRepository.findById("nonexistent")).thenReturn(Mono.empty());

        // When / Then: the Mono emits false (nothing was deleted)
        StepVerifier.create(articleService.deleteById("nonexistent"))
                .expectNext(false)
                .verifyComplete();

        verify(articleRepository, times(1)).findById("nonexistent");
        // deleteById must NEVER be called when the article was not found
        verify(articleRepository, never()).deleteById(anyString());
    }
}
