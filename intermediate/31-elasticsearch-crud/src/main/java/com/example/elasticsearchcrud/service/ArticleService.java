package com.example.elasticsearchcrud.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.elasticsearchcrud.domain.Article;
import com.example.elasticsearchcrud.dto.ArticleRequest;
import com.example.elasticsearchcrud.repository.ArticleRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Business logic layer for {@link Article} document operations.
 *
 * <p>This service bridges the HTTP layer (controller) and the data layer (repository +
 * Elasticsearch client). It is responsible for:
 * <ul>
 *   <li>Mapping incoming {@link ArticleRequest} DTOs to {@link Article} domain objects.</li>
 *   <li>Delegating CRUD operations to {@link ArticleRepository} (which uses Spring Data's
 *       generated implementations backed by the Elasticsearch REST client).</li>
 *   <li>Performing advanced full-text search via the low-level {@link ElasticsearchClient}
 *       Java API client, which gives direct access to Elasticsearch query DSL features
 *       beyond what Spring Data's query derivation supports.</li>
 *   <li>Maintaining {@code createdAt} / {@code updatedAt} timestamps on every write.</li>
 * </ul>
 *
 * <p>{@link Service} marks this class as a Spring-managed service bean, which means
 * Spring creates a singleton instance and injects it wherever it is autowired.
 */
@Service
public class ArticleService {

    /**
     * Spring Data Elasticsearch repository – provides derived-query methods and
     * all standard CRUD operations (save, findById, findAll, deleteById, etc.).
     */
    private final ArticleRepository articleRepository;

    /**
     * Low-level Elasticsearch Java API client auto-configured by Spring Boot.
     * Used for advanced multi-field full-text search queries that cannot be
     * expressed via Spring Data's method-naming convention alone.
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * Constructor injection – preferred over field injection because it makes
     * dependencies explicit and facilitates unit testing with mocks.
     *
     * @param articleRepository    the Spring Data repository
     * @param elasticsearchClient  the low-level ES Java API client
     */
    public ArticleService(ArticleRepository articleRepository,
                          ElasticsearchClient elasticsearchClient) {
        this.articleRepository = articleRepository;
        this.elasticsearchClient = elasticsearchClient;
    }

    // ── Read operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all articles stored in the Elasticsearch index.
     *
     * <p>Delegates to {@link ArticleRepository#findAll()}, which generates a
     * {@code match_all} query against the {@code articles} index.
     *
     * @return list of all articles (empty list if index is empty)
     */
    public List<Article> findAll() {
        // Collect the Iterable returned by Spring Data into a mutable List
        return StreamSupport
                .stream(articleRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Find a single article by its Elasticsearch document ID.
     *
     * @param id the document ID (UUID string assigned by Elasticsearch)
     * @return an {@link Optional} containing the article if found, or empty if not
     */
    public Optional<Article> findById(String id) {
        return articleRepository.findById(id);
    }

    /**
     * Find all articles written by a specific author (exact-match keyword query).
     *
     * @param author the author name to match exactly
     * @return list of articles by that author
     */
    public List<Article> findByAuthor(String author) {
        return articleRepository.findByAuthor(author);
    }

    /**
     * Find all articles belonging to a specific category (exact-match keyword query).
     *
     * @param category the category to match exactly (e.g. "technology")
     * @return list of articles in that category
     */
    public List<Article> findByCategory(String category) {
        return articleRepository.findByCategory(category);
    }

    /**
     * Find articles with a view count strictly greater than the given threshold.
     *
     * <p>Useful for finding "popular" articles above a minimum engagement threshold.
     *
     * @param threshold articles with more views than this value are returned
     * @return list of articles above the view threshold
     */
    public List<Article> findByViewCountGreaterThan(int threshold) {
        return articleRepository.findByViewCountGreaterThan(threshold);
    }

    /**
     * Perform a full-text search across both the {@code title} and {@code content}
     * fields using Elasticsearch's {@code multi_match} query.
     *
     * <p>A {@code multi_match} query is more powerful than a Spring Data derived
     * query because it simultaneously searches multiple fields, applies the standard
     * analyser (lowercasing, tokenisation, stemming), and can boost certain fields
     * (e.g. title matches outweigh content matches).
     *
     * <p>This method uses the <em>Elasticsearch Java API Client</em> (the new
     * strongly-typed client introduced in Elasticsearch 8.x) rather than the
     * deprecated {@code RestHighLevelClient}.
     *
     * @param query the search text to look for in titles and content
     * @return list of matching articles ranked by Elasticsearch relevance score
     * @throws RuntimeException wrapping any {@link IOException} from the ES client
     */
    public List<Article> fullTextSearch(String query) {
        try {
            // Build and execute a multi_match search request via the Java API client.
            // The lambda-based fluent builder API mirrors the Elasticsearch query DSL:
            //   { "query": { "multi_match": { "query": "...", "fields": ["title", "content"] } } }
            SearchResponse<Article> response = elasticsearchClient.search(
                    s -> s
                            // Target the "articles" index
                            .index("articles")
                            // Define the query
                            .query(q -> q
                                    .multiMatch(mm -> mm
                                            // The text to search for
                                            .query(query)
                                            // Search across both title and content fields.
                                            // Elasticsearch analyses and tokenises each field
                                            // before comparing, so "Spring Boot" matches
                                            // documents containing "spring" or "boot" in
                                            // either field.
                                            .fields("title", "content")
                                    )
                            ),
            // Tell the client how to deserialise response hits into Article objects
            Article.class
            );

            // Extract the source documents from the search hits and collect into a list.
            // Each Hit<Article> contains the document source, score, and metadata.
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            // Wrap the checked IOException in a runtime exception so callers
            // (including the controller) do not need to declare it.
            throw new RuntimeException("Elasticsearch query failed: " + e.getMessage(), e);
        }
    }

    // ── Write operations ──────────────────────────────────────────────────────────

    /**
     * Create a new article in the Elasticsearch index.
     *
     * <p>Maps the incoming {@link ArticleRequest} DTO to a new {@link Article} domain
     * object and persists it. Elasticsearch assigns a UUID document ID automatically.
     *
     * @param request the validated DTO from the HTTP request body
     * @return the persisted article with its Elasticsearch-assigned ID
     */
    public Article create(ArticleRequest request) {
        // Map the request DTO to a domain object.
        // The Article constructor sets createdAt and updatedAt to Instant.now().
        Article article = new Article(
                request.title(),
                request.content(),
                request.author(),
                request.category(),
                request.viewCount()
        );
        // Persist to Elasticsearch; Spring Data calls the ES "index" API under the hood
        return articleRepository.save(article);
    }

    /**
     * Update an existing article by ID.
     *
     * <p>Follows the "fetch-modify-save" pattern:
     * <ol>
     *   <li>Load the existing document from Elasticsearch (returns empty if not found).</li>
     *   <li>Apply all field changes from the request DTO.</li>
     *   <li>Refresh {@code updatedAt} to the current time.</li>
     *   <li>Save the modified document back to Elasticsearch.</li>
     * </ol>
     *
     * @param id      the document ID of the article to update
     * @param request the new field values from the HTTP request body
     * @return an {@link Optional} containing the updated article, or empty if not found
     */
    public Optional<Article> update(String id, ArticleRequest request) {
        // Load the existing document; return empty Optional if it does not exist
        return articleRepository.findById(id).map(existing -> {
            // Apply changes from the request DTO to the loaded entity
            existing.setTitle(request.title());
            existing.setContent(request.content());
            existing.setAuthor(request.author());
            existing.setCategory(request.category());
            existing.setViewCount(request.viewCount());
            // Refresh the updatedAt timestamp to track when changes were made
            existing.setUpdatedAt(Instant.now());
            // Persist: Spring Data calls ES "index" (upsert by ID)
            return articleRepository.save(existing);
        });
    }

    /**
     * Delete an article from the Elasticsearch index by ID.
     *
     * @param id the document ID of the article to delete
     * @return {@code true} if the article existed and was deleted; {@code false} otherwise
     */
    public boolean deleteById(String id) {
        // Check existence first to return a meaningful boolean to the controller
        if (!articleRepository.existsById(id)) {
            return false;
        }
        articleRepository.deleteById(id);
        return true;
    }
}
