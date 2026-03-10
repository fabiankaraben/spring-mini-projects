package com.example.reactivewebfluxapi.service;

import com.example.reactivewebfluxapi.domain.Article;
import com.example.reactivewebfluxapi.dto.ArticleRequest;
import com.example.reactivewebfluxapi.repository.ArticleRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Service layer containing the business logic for article management.
 *
 * <p>This class sits between the HTTP layer ({@link com.example.reactivewebfluxapi.controller.ArticleController})
 * and the data access layer ({@link ArticleRepository}). Key responsibilities:
 * <ul>
 *   <li>Map request DTOs ({@link ArticleRequest}) to domain entities ({@link Article}).</li>
 *   <li>Orchestrate reactive streams using {@link Mono} and {@link Flux} operators.</li>
 *   <li>Encapsulate business rules (e.g., preserving {@code createdAt} on update).</li>
 * </ul>
 *
 * <p><strong>Reactive programming model:</strong><br>
 * Every method returns a {@link Mono} (0 or 1 item) or a {@link Flux} (0..N items).
 * These are <em>lazy publishers</em> — no work happens until a subscriber subscribes.
 * The WebFlux controller subscribes when it serialises the response to the client.
 * This "cold" nature means the pipeline only executes once per HTTP request.
 *
 * <p><strong>Operator glossary used in this class:</strong>
 * <ul>
 *   <li>{@code flatMap} – transform each item into a new publisher and flatten results.</li>
 *   <li>{@code map} – transform each item synchronously (no new publisher).</li>
 *   <li>{@code switchIfEmpty} – emit a fallback publisher when the upstream is empty.</li>
 *   <li>{@code filter} – pass items downstream only if a predicate returns {@code true}.</li>
 * </ul>
 */
@Service
public class ArticleService {

    private final ArticleRepository articleRepository;

    /**
     * Constructor injection makes the dependency explicit and enables unit testing
     * without a Spring context (just pass a mock repository).
     *
     * @param articleRepository reactive MongoDB repository for articles
     */
    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    // ── Read operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all articles in the collection as a reactive stream.
     *
     * <p>Returns a {@link Flux} that emits each article document as it arrives from
     * MongoDB — back-pressure is automatically handled by Project Reactor so the
     * application never loads the entire collection into heap memory at once.
     *
     * @return a Flux emitting all articles
     */
    public Flux<Article> findAll() {
        return articleRepository.findAll();
    }

    /**
     * Retrieve a single article by its MongoDB ObjectId.
     *
     * <p>Returns a {@link Mono} that either emits the article or completes empty.
     * The controller maps an empty Mono to HTTP 404.
     *
     * @param id the MongoDB document ID (hex string)
     * @return a Mono emitting the article, or empty if not found
     */
    public Mono<Article> findById(String id) {
        return articleRepository.findById(id);
    }

    /**
     * Find all articles in a given category.
     *
     * @param category the category to filter by (case-sensitive)
     * @return a Flux emitting matching articles
     */
    public Flux<Article> findByCategory(String category) {
        return articleRepository.findByCategory(category);
    }

    /**
     * Search articles by keyword in the title (case-insensitive).
     *
     * <p>Delegates to the repository which generates a MongoDB regex query.
     * Useful for a search-bar feature.
     *
     * @param keyword text to search for within article titles
     * @return a Flux emitting articles whose titles contain the keyword
     */
    public Flux<Article> searchByTitle(String keyword) {
        return articleRepository.findByTitleContainingIgnoreCase(keyword);
    }

    /**
     * Retrieve all published articles (i.e., articles visible to readers).
     *
     * @return a Flux emitting published articles only
     */
    public Flux<Article> findPublished() {
        // Pass true to filter only published articles
        return articleRepository.findByPublished(true);
    }

    /**
     * Retrieve all articles written by a specific author.
     *
     * @param author the author's display name (case-sensitive)
     * @return a Flux emitting articles by the given author
     */
    public Flux<Article> findByAuthor(String author) {
        return articleRepository.findByAuthor(author);
    }

    /**
     * Count the total number of articles written by a given author.
     *
     * <p>Returns a {@link Mono}{@code <Long>} that emits a single count value.
     * Using a count aggregation is more efficient than fetching all documents
     * and counting them in application memory.
     *
     * @param author the author's display name
     * @return a Mono emitting the count of articles by that author
     */
    public Mono<Long> countByAuthor(String author) {
        return articleRepository.countByAuthor(author);
    }

    // ── Write operations ──────────────────────────────────────────────────────────

    /**
     * Create and persist a new article document in MongoDB.
     *
     * <p>Maps the request DTO to a new {@link Article} domain object.
     * The {@code id} is left {@code null} so MongoDB generates an ObjectId.
     * {@code createdAt} and {@code updatedAt} are initialised in the entity constructor.
     *
     * <p>Reactive pipeline: {@code Mono.just(entity)} wraps the entity in a publisher,
     * then {@code flatMap(save)} subscribes to the save operation and emits the saved
     * entity (with generated {@code id}) downstream.
     *
     * @param request the article data from the HTTP request body
     * @return a Mono emitting the persisted article with its MongoDB-generated id
     */
    public Mono<Article> create(ArticleRequest request) {
        // Map DTO → domain entity; id is null so MongoDB assigns an ObjectId on insert
        Article article = new Article(
                request.getTitle(),
                request.getContent(),
                request.getAuthor(),
                request.getCategory(),
                request.isPublished()
        );
        // save() returns a Mono that emits the saved entity (with generated id) on completion
        return articleRepository.save(article);
    }

    /**
     * Update an existing article document (full replacement — PUT semantics).
     *
     * <p>Reactive pipeline:
     * <ol>
     *   <li>{@code findById(id)} — look up the existing document; returns empty Mono if absent.</li>
     *   <li>{@code map(...)} — mutate the entity in-place with new field values.</li>
     *   <li>{@code flatMap(save)} — persist the mutated entity and emit the result.</li>
     * </ol>
     * Returns an empty {@link Mono} if the article is not found; the controller maps
     * this to HTTP 404.
     *
     * @param id      the MongoDB document ID of the article to update
     * @param request the new field values
     * @return a Mono emitting the updated article, or empty if not found
     */
    public Mono<Article> update(String id, ArticleRequest request) {
        return articleRepository.findById(id)
                // map() mutates the fetched entity synchronously — no new publisher
                .map(existing -> {
                    existing.setTitle(request.getTitle());
                    existing.setContent(request.getContent());
                    existing.setAuthor(request.getAuthor());
                    existing.setCategory(request.getCategory());
                    existing.setPublished(request.isPublished());
                    // Refresh the modification timestamp; createdAt is left unchanged
                    existing.setUpdatedAt(Instant.now());
                    return existing;
                })
                // flatMap saves the mutated entity and passes the saved result downstream
                .flatMap(articleRepository::save);
    }

    /**
     * Delete an article document by its MongoDB ObjectId.
     *
     * <p>Reactive pipeline:
     * <ol>
     *   <li>{@code findById(id)} — verify the article exists.</li>
     *   <li>{@code flatMap(deleteById)} — delete only if found; emits {@code true}.</li>
     *   <li>{@code switchIfEmpty(Mono.just(false))} — emit {@code false} if the article
     *       was not found (so the controller can respond with HTTP 404 instead of 204).</li>
     * </ol>
     *
     * @param id the MongoDB document ID of the article to delete
     * @return a Mono emitting {@code true} if deleted, or {@code false} if not found
     */
    public Mono<Boolean> deleteById(String id) {
        return articleRepository.findById(id)
                // Only delete if the article actually exists
                .flatMap(article -> articleRepository.deleteById(article.getId())
                        // deleteById returns Mono<Void>; map it to true to signal success
                        .thenReturn(true))
                // If findById emitted empty, emit false to signal "not found"
                .switchIfEmpty(Mono.just(false));
    }
}
