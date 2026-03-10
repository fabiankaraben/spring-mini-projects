package com.example.elasticsearchcrud.repository;

import com.example.elasticsearchcrud.domain.Article;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * Spring Data Elasticsearch repository for {@link Article} documents.
 *
 * <p>By extending {@link ElasticsearchRepository}, Spring Data automatically provides
 * implementations for all standard CRUD operations at runtime:
 * <ul>
 *   <li>{@code save(Article)} – indexes (creates or replaces) a document.</li>
 *   <li>{@code findById(String)} – retrieves a document by its Elasticsearch ID.</li>
 *   <li>{@code findAll()} – returns all documents from the index.</li>
 *   <li>{@code deleteById(String)} – removes a document from the index.</li>
 *   <li>{@code count()} – returns the total number of documents.</li>
 *   <li>{@code existsById(String)} – checks if a document with the given ID exists.</li>
 * </ul>
 *
 * <p>The methods declared below use Spring Data's <em>query derivation</em> mechanism:
 * Spring Data Elasticsearch parses the method name and generates the corresponding
 * Elasticsearch query automatically — no implementation code is needed.
 *
 * <p>Type parameters:
 * <ul>
 *   <li>{@code Article} – the document type.</li>
 *   <li>{@code String} – the type of the document's {@code @Id} field.</li>
 * </ul>
 */
public interface ArticleRepository extends ElasticsearchRepository<Article, String> {

    /**
     * Find all articles written by a specific author.
     *
     * <p>Spring Data derives an Elasticsearch {@code term} query on the {@code author}
     * keyword field: {@code { "term": { "author": "<authorName>" } }}.
     * This is an exact-match query (no tokenisation), which is why {@code author}
     * is mapped as {@code FieldType.Keyword} in {@link Article}.
     *
     * @param author the exact author name to match
     * @return list of articles by that author, or an empty list if none found
     */
    List<Article> findByAuthor(String author);

    /**
     * Find all articles belonging to a specific category.
     *
     * <p>Derives a {@code term} query on the {@code category} keyword field.
     *
     * @param category the exact category name (e.g. "technology", "health")
     * @return list of articles in that category, or an empty list
     */
    List<Article> findByCategory(String category);

    /**
     * Find articles whose title contains the given keyword (case-insensitive).
     *
     * <p>Spring Data derives a {@code match} query on the {@code title} text field.
     * Because {@code title} is mapped as {@code FieldType.Text}, Elasticsearch
     * analyses (tokenises and lowercases) the query term at search time, enabling
     * case-insensitive partial matches.
     *
     * @param keyword the search term to look for within titles
     * @return list of articles with matching titles
     */
    List<Article> findByTitleContaining(String keyword);

    /**
     * Find articles with a view count strictly greater than the given threshold.
     *
     * <p>Spring Data derives an Elasticsearch {@code range} query:
     * {@code { "range": { "viewCount": { "gt": <threshold> } } }}.
     *
     * @param threshold minimum view count (exclusive); articles with more views are returned
     * @return list of articles whose {@code viewCount} exceeds the threshold
     */
    List<Article> findByViewCountGreaterThan(int threshold);
}
