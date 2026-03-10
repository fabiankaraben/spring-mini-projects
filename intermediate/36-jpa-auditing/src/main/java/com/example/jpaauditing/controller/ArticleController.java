package com.example.jpaauditing.controller;

import com.example.jpaauditing.dto.ArticleRequest;
import com.example.jpaauditing.dto.ArticleResponse;
import com.example.jpaauditing.service.ArticleService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes CRUD endpoints for {@code Article} resources.
 *
 * <p>The controller's job is thin by design: it handles HTTP mapping, delegates
 * all business logic to {@link ArticleService}, and maps service results to
 * appropriate HTTP responses. The JPA Auditing feature is transparent here —
 * the {@code createdAt} and {@code updatedAt} fields simply appear in the
 * serialized JSON because they are present on the {@code ArticleResponse} DTO.
 *
 * <p>Base path: {@code /api/articles}
 */
@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;

    /**
     * Constructor injection — single constructor; no {@code @Autowired} needed.
     *
     * @param articleService the service handling article business logic
     */
    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    /**
     * {@code GET /api/articles} — list all articles.
     *
     * <p>Optional {@code ?author=} query parameter to filter by author.
     *
     * <p>Example:
     * <pre>
     * curl http://localhost:8080/api/articles
     * curl http://localhost:8080/api/articles?author=Jane
     * </pre>
     *
     * @param author optional author filter
     * @return 200 OK with list of articles
     */
    @GetMapping
    public List<ArticleResponse> listAll(@RequestParam(required = false) String author) {
        if (author != null && !author.isBlank()) {
            return articleService.findByAuthor(author);
        }
        return articleService.findAll();
    }

    /**
     * {@code GET /api/articles/{id}} — retrieve a single article by ID.
     *
     * <p>Example:
     * <pre>
     * curl http://localhost:8080/api/articles/1
     * </pre>
     *
     * @param id the article's primary key
     * @return 200 OK with the article, or 404 if not found
     */
    @GetMapping("/{id}")
    public ArticleResponse getById(@PathVariable Long id) {
        return articleService.findById(id);
    }

    /**
     * {@code POST /api/articles} — create a new article.
     *
     * <p>The response will contain {@code createdAt} and {@code updatedAt} fields
     * automatically populated by the JPA Auditing infrastructure — no manual
     * timestamp code is needed anywhere.
     *
     * <p>Example:
     * <pre>
     * curl -X POST http://localhost:8080/api/articles \
     *      -H "Content-Type: application/json" \
     *      -d '{"title":"Hello World","content":"My first article","author":"Jane"}'
     * </pre>
     *
     * @param request validated inbound DTO
     * @return 201 Created with the persisted article including audit timestamps
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleResponse create(@Valid @RequestBody ArticleRequest request) {
        return articleService.create(request);
    }

    /**
     * {@code PUT /api/articles/{id}} — replace an existing article's data.
     *
     * <p>After this call, {@code updatedAt} will be refreshed to the current instant
     * while {@code createdAt} remains unchanged.
     *
     * <p>Example:
     * <pre>
     * curl -X PUT http://localhost:8080/api/articles/1 \
     *      -H "Content-Type: application/json" \
     *      -d '{"title":"Updated Title","content":"Updated content","author":"Jane"}'
     * </pre>
     *
     * @param id      the ID of the article to update
     * @param request validated inbound DTO with new values
     * @return 200 OK with the updated article
     */
    @PutMapping("/{id}")
    public ArticleResponse update(@PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
        return articleService.update(id, request);
    }

    /**
     * {@code DELETE /api/articles/{id}} — remove an article.
     *
     * <p>Example:
     * <pre>
     * curl -X DELETE http://localhost:8080/api/articles/1
     * </pre>
     *
     * @param id the ID of the article to delete
     * @return 204 No Content on success, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
