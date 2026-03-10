package com.example.elasticsearchcrud.controller;

import com.example.elasticsearchcrud.domain.Article;
import com.example.elasticsearchcrud.dto.ArticleRequest;
import com.example.elasticsearchcrud.exception.ArticleNotFoundException;
import com.example.elasticsearchcrud.service.ArticleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing CRUD and search endpoints for {@link Article} documents.
 *
 * <p>{@link RestController} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Controller} – marks this class as a Spring MVC controller.</li>
 *   <li>{@code @ResponseBody} – serialises the return value of every handler method
 *       directly to the HTTP response body as JSON (via Jackson).</li>
 * </ul>
 *
 * <p>{@link RequestMapping}({@code "/api/articles"}) sets the base URL path for all
 * handler methods in this controller. Individual methods append further path segments.
 *
 * <p>HTTP → Method mapping:
 * <pre>
 *   GET    /api/articles                    → list all articles
 *   GET    /api/articles/{id}               → get one article by ID
 *   GET    /api/articles/search?q=...       → full-text multi-field search
 *   GET    /api/articles/author/{author}    → filter by author (exact match)
 *   GET    /api/articles/category/{cat}     → filter by category (exact match)
 *   GET    /api/articles/popular?threshold= → filter by minimum view count
 *   POST   /api/articles                    → create a new article
 *   PUT    /api/articles/{id}               → update an existing article
 *   DELETE /api/articles/{id}               → delete an article
 * </pre>
 */
@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    /**
     * The service that contains all business logic and Elasticsearch operations.
     * Injected via constructor for testability (can be mocked in unit tests).
     */
    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    // ── GET /api/articles ─────────────────────────────────────────────────────────

    /**
     * Retrieve all articles from the Elasticsearch index.
     *
     * @return HTTP 200 with a JSON array of all articles (may be empty)
     */
    @GetMapping
    public ResponseEntity<List<Article>> getAllArticles() {
        List<Article> articles = articleService.findAll();
        // Always return 200 even when the list is empty — empty array is valid JSON
        return ResponseEntity.ok(articles);
    }

    // ── GET /api/articles/{id} ────────────────────────────────────────────────────

    /**
     * Retrieve a single article by its Elasticsearch document ID.
     *
     * @param id the document ID from the URL path
     * @return HTTP 200 with the article, or HTTP 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Article> getArticleById(@PathVariable String id) {
        // orElseThrow triggers the GlobalExceptionHandler → 404 response
        Article article = articleService.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
        return ResponseEntity.ok(article);
    }

    // ── GET /api/articles/search?q=... ────────────────────────────────────────────

    /**
     * Perform a full-text search across article titles and content.
     *
     * <p>Uses Elasticsearch's {@code multi_match} query under the hood, which
     * tokenises and analyses the query string against both the {@code title} and
     * {@code content} fields. Results are ranked by relevance score.
     *
     * @param q the search query string (required)
     * @return HTTP 200 with a list of matching articles
     */
    @GetMapping("/search")
    public ResponseEntity<List<Article>> searchArticles(@RequestParam String q) {
        List<Article> results = articleService.fullTextSearch(q);
        return ResponseEntity.ok(results);
    }

    // ── GET /api/articles/author/{author} ────────────────────────────────────────

    /**
     * Retrieve all articles written by a specific author (exact-match keyword query).
     *
     * @param author the exact author name from the URL path
     * @return HTTP 200 with a list of matching articles (may be empty)
     */
    @GetMapping("/author/{author}")
    public ResponseEntity<List<Article>> getArticlesByAuthor(@PathVariable String author) {
        List<Article> articles = articleService.findByAuthor(author);
        return ResponseEntity.ok(articles);
    }

    // ── GET /api/articles/category/{category} ────────────────────────────────────

    /**
     * Retrieve all articles in a specific category (exact-match keyword query).
     *
     * @param category the exact category name from the URL path
     * @return HTTP 200 with a list of matching articles (may be empty)
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<Article>> getArticlesByCategory(@PathVariable String category) {
        List<Article> articles = articleService.findByCategory(category);
        return ResponseEntity.ok(articles);
    }

    // ── GET /api/articles/popular?threshold=... ───────────────────────────────────

    /**
     * Retrieve articles with a view count greater than the given threshold.
     *
     * @param threshold the minimum view count (exclusive); defaults to 0
     * @return HTTP 200 with a list of popular articles
     */
    @GetMapping("/popular")
    public ResponseEntity<List<Article>> getPopularArticles(
            @RequestParam(defaultValue = "0") int threshold) {
        List<Article> articles = articleService.findByViewCountGreaterThan(threshold);
        return ResponseEntity.ok(articles);
    }

    // ── POST /api/articles ────────────────────────────────────────────────────────

    /**
     * Create and index a new article.
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body; if any constraint
     * is violated (e.g. blank title), Spring MVC throws {@link org.springframework.web.bind.MethodArgumentNotValidException}
     * which the {@link com.example.elasticsearchcrud.exception.GlobalExceptionHandler} maps to HTTP 400.
     *
     * @param request the validated article data from the JSON request body
     * @return HTTP 201 Created with the indexed article (including the generated document ID)
     */
    @PostMapping
    public ResponseEntity<Article> createArticle(@Valid @RequestBody ArticleRequest request) {
        Article created = articleService.create(request);
        // HTTP 201 Created signals that a new resource was successfully created
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── PUT /api/articles/{id} ────────────────────────────────────────────────────

    /**
     * Update an existing article by its document ID.
     *
     * @param id      the document ID of the article to update
     * @param request the new field values from the JSON request body
     * @return HTTP 200 with the updated article, or HTTP 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Article> updateArticle(@PathVariable String id,
                                                  @Valid @RequestBody ArticleRequest request) {
        Article updated = articleService.update(id, request)
                .orElseThrow(() -> new ArticleNotFoundException(id));
        return ResponseEntity.ok(updated);
    }

    // ── DELETE /api/articles/{id} ─────────────────────────────────────────────────

    /**
     * Delete an article from the Elasticsearch index by its document ID.
     *
     * @param id the document ID of the article to delete
     * @return HTTP 204 No Content on success, or HTTP 404 if the article does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArticle(@PathVariable String id) {
        boolean deleted = articleService.deleteById(id);
        if (!deleted) {
            throw new ArticleNotFoundException(id);
        }
        // HTTP 204 No Content: success, nothing to return
        return ResponseEntity.noContent().build();
    }
}
