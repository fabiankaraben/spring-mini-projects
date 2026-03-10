package com.example.reactivewebfluxapi.repository;

import com.example.reactivewebfluxapi.domain.Article;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive MongoDB repository for {@link Article} documents.
 *
 * <p>Extends {@link ReactiveMongoRepository} which is the reactive counterpart of the
 * traditional {@code MongoRepository}. The key difference:
 * <ul>
 *   <li>{@code MongoRepository} — blocks the calling thread until MongoDB responds.</li>
 *   <li>{@code ReactiveMongoRepository} — returns {@link Mono} or {@link Flux} types,
 *       which are lazy, non-blocking publishers. The calling thread is released
 *       immediately and a callback is invoked when the database result arrives.</li>
 * </ul>
 *
 * <p>Spring Data auto-generates the implementation at startup by parsing method names
 * (derived queries) or evaluating {@link Query} annotations. No boilerplate SQL or
 * MongoDB shell code is required.
 *
 * <p>Generic type parameters: {@code <Article, String>}
 * <ul>
 *   <li>First – the entity type managed by this repository.</li>
 *   <li>Second – the type of the entity's {@code @Id} field (MongoDB ObjectId as String).</li>
 * </ul>
 */
@Repository
public interface ArticleRepository extends ReactiveMongoRepository<Article, String> {

    /**
     * Find all articles in a given category (exact, case-sensitive match).
     *
     * <p>Spring Data derives the MongoDB query {@code { category: <category> }} from
     * the method name {@code findByCategory}. Returns a {@link Flux} because the
     * result may contain zero or more articles.
     *
     * @param category the category to filter by
     * @return a Flux emitting matching articles, completing empty if none found
     */
    Flux<Article> findByCategory(String category);

    /**
     * Find all articles whose title contains the given text, case-insensitively.
     *
     * <p>Derived query method: Spring Data translates {@code findByTitleContainingIgnoreCase}
     * into a MongoDB regex query: {@code { title: { $regex: <keyword>, $options: "i" } }}.
     *
     * @param keyword the text to search for within article titles
     * @return a Flux emitting articles whose titles contain {@code keyword}
     */
    Flux<Article> findByTitleContainingIgnoreCase(String keyword);

    /**
     * Find all published (or unpublished) articles.
     *
     * <p>Derived from the field name: {@code { published: <published> }}.
     * Useful for a "published articles feed" (pass {@code true}) or a
     * "drafts list" (pass {@code false}).
     *
     * @param published {@code true} to return published articles; {@code false} for drafts
     * @return a Flux emitting articles matching the given publication status
     */
    Flux<Article> findByPublished(boolean published);

    /**
     * Count how many articles a given author has written.
     *
     * <p>Aggregates without loading all documents into memory.
     * Returns a {@link Mono} because a count is always a single value.
     *
     * @param author the author's display name
     * @return a Mono emitting the count of articles by that author
     */
    Mono<Long> countByAuthor(String author);

    /**
     * Find all articles written by a specific author.
     *
     * <p>Uses a {@link Query} annotation with a MongoDB JSON query string.
     * The {@code ?0} placeholder is replaced at runtime by the first method parameter.
     * This is equivalent to writing the derived method {@code findByAuthor(String author)},
     * but demonstrates the {@code @Query} annotation as an educational example.
     *
     * @param author the author's display name
     * @return a Flux emitting all articles by the given author
     */
    @Query("{ 'author': ?0 }")
    Flux<Article> findByAuthor(String author);
}
