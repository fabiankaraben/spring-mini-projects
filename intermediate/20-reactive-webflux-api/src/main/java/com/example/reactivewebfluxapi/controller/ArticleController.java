package com.example.reactivewebfluxapi.controller;

import com.example.reactivewebfluxapi.domain.Article;
import com.example.reactivewebfluxapi.dto.ArticleRequest;
import com.example.reactivewebfluxapi.service.ArticleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller exposing non-blocking HTTP endpoints for article management.
 *
 * <p>Annotations:
 * <ul>
 *   <li>{@code @RestController} – combines {@code @Controller} and {@code @ResponseBody}.
 *       Every return value is automatically serialised to JSON by Jackson.</li>
 *   <li>{@code @RequestMapping("/api/articles")} – all endpoints in this class are
 *       prefixed with {@code /api/articles}.</li>
 * </ul>
 *
 * <p><strong>How WebFlux handles reactive return types:</strong><br>
 * Spring WebFlux subscribes to the returned {@link Mono} or {@link Flux} and writes
 * the emitted items to the HTTP response asynchronously. The calling thread is never
 * blocked waiting for the database — it handles other requests while the I/O completes.
 *
 * <p><strong>{@code Mono<Void>} vs {@code void}:</strong><br>
 * WebFlux controllers should return {@code Mono<Void>} for 204 No Content responses
 * so the reactive pipeline stays unbroken and back-pressure propagates correctly.
 */
@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;

    /**
     * Constructor injection — makes dependencies explicit and testable without a Spring context.
     *
     * @param articleService the service containing article business logic
     */
    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    // ── GET /api/articles ─────────────────────────────────────────────────────────

    /**
     * List all articles.
     *
     * <p>Returns a {@link Flux} that streams each article as JSON. WebFlux writes each
     * item to the response body as it arrives from MongoDB — no need to buffer all
     * documents in memory.
     *
     * <p>HTTP 200 OK is the default status when the method completes without error.
     *
     * @return a Flux emitting all articles
     */
    @GetMapping
    public Flux<Article> getAllArticles() {
        return articleService.findAll();
    }

    // ── GET /api/articles/{id} ────────────────────────────────────────────────────

    /**
     * Get a single article by its MongoDB ObjectId.
     *
     * <p>{@code switchIfEmpty} transforms an empty {@link Mono} (article not found)
     * into a {@link Mono} that emits an error, which WebFlux converts to HTTP 404.
     *
     * @param id the MongoDB document ID
     * @return a Mono emitting the article, or 404 if not found
     */
    @GetMapping("/{id}")
    public Mono<Article> getArticleById(@PathVariable String id) {
        return articleService.findById(id)
                // switchIfEmpty triggers when the Mono is empty (article not found).
                // Mono.error wraps a ResponseStatusException so WebFlux maps it to HTTP 404.
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: " + id)));
    }

    // ── GET /api/articles/category/{category} ─────────────────────────────────────

    /**
     * List all articles in a given category.
     *
     * @param category the category to filter by (path variable)
     * @return a Flux emitting articles in the specified category
     */
    @GetMapping("/category/{category}")
    public Flux<Article> getByCategory(@PathVariable String category) {
        return articleService.findByCategory(category);
    }

    // ── GET /api/articles/search?keyword=... ──────────────────────────────────────

    /**
     * Search articles by keyword in the title (case-insensitive).
     *
     * <p>The {@code keyword} query parameter is required — Spring returns 400 automatically
     * if it is missing, because {@code @RequestParam} is required by default.
     *
     * @param keyword text to search for within article titles
     * @return a Flux emitting matching articles
     */
    @GetMapping("/search")
    public Flux<Article> searchByTitle(@RequestParam String keyword) {
        return articleService.searchByTitle(keyword);
    }

    // ── GET /api/articles/published ───────────────────────────────────────────────

    /**
     * List all published articles (i.e., articles visible to readers).
     *
     * @return a Flux emitting published articles only
     */
    @GetMapping("/published")
    public Flux<Article> getPublished() {
        return articleService.findPublished();
    }

    // ── GET /api/articles/author/{author} ─────────────────────────────────────────

    /**
     * List all articles written by a specific author.
     *
     * @param author the author's display name (path variable)
     * @return a Flux emitting articles by the specified author
     */
    @GetMapping("/author/{author}")
    public Flux<Article> getByAuthor(@PathVariable String author) {
        return articleService.findByAuthor(author);
    }

    // ── GET /api/articles/author/{author}/count ───────────────────────────────────

    /**
     * Count how many articles a given author has written.
     *
     * <p>Returns a {@link Mono}{@code <Long>} — a single count value that WebFlux
     * serialises as a plain JSON number (e.g., {@code 3}).
     *
     * @param author the author's display name
     * @return a Mono emitting the count of articles by that author
     */
    @GetMapping("/author/{author}/count")
    public Mono<Long> countByAuthor(@PathVariable String author) {
        return articleService.countByAuthor(author);
    }

    // ── POST /api/articles ────────────────────────────────────────────────────────

    /**
     * Create a new article.
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body.
     * If any constraint is violated, Spring WebFlux returns HTTP 400 Bad Request
     * before this method is even invoked.
     *
     * <p>{@code @ResponseStatus(CREATED)} changes the default 200 status to 201
     * when the Mono completes successfully.
     *
     * @param request the article data from the request body
     * @return a Mono emitting the created article with its generated id
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Article> createArticle(@Valid @RequestBody ArticleRequest request) {
        return articleService.create(request);
    }

    // ── PUT /api/articles/{id} ────────────────────────────────────────────────────

    /**
     * Update an existing article (full replacement — PUT semantics).
     *
     * <p>If the article is not found, the service returns an empty {@link Mono}.
     * {@code switchIfEmpty} converts this to a 404 error.
     *
     * @param id      the MongoDB document ID of the article to update
     * @param request the new field values
     * @return a Mono emitting the updated article, or 404 if not found
     */
    @PutMapping("/{id}")
    public Mono<Article> updateArticle(@PathVariable String id,
                                       @Valid @RequestBody ArticleRequest request) {
        return articleService.update(id, request)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: " + id)));
    }

    // ── DELETE /api/articles/{id} ─────────────────────────────────────────────────

    /**
     * Delete an article by its MongoDB ObjectId.
     *
     * <p>The service returns {@code Mono<Boolean>}:
     * <ul>
     *   <li>{@code true} — article existed and was deleted → HTTP 204 No Content.</li>
     *   <li>{@code false} — article was not found → HTTP 404 Not Found.</li>
     * </ul>
     *
     * <p>{@code flatMap} maps the boolean result to the appropriate HTTP response.
     * {@code Mono.empty()} signals "no response body" which WebFlux maps to 204
     * when combined with {@code @ResponseStatus(NO_CONTENT)}.
     *
     * @param id the MongoDB document ID of the article to delete
     * @return an empty Mono (HTTP 204) or a 404 error
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteArticle(@PathVariable String id) {
        return articleService.deleteById(id)
                .flatMap(deleted -> {
                    if (deleted) {
                        // Article was deleted — return empty Mono (HTTP 204 No Content)
                        return Mono.<Void>empty();
                    } else {
                        // Article was not found — signal HTTP 404
                        return Mono.error(
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: " + id));
                    }
                });
    }
}
