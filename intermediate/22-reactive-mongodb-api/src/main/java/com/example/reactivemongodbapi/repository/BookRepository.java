package com.example.reactivemongodbapi.repository;

import com.example.reactivemongodbapi.domain.Book;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


/**
 * Reactive MongoDB repository for {@link Book} documents.
 *
 * <p>Extends {@link ReactiveCrudRepository} which is the reactive counterpart of the
 * traditional {@code CrudRepository} (used with JPA / blocking MongoDB). The key difference:
 * <ul>
 *   <li>{@code CrudRepository} (blocking) — blocks the calling thread until the database
 *       responds. Requires one thread per concurrent request.</li>
 *   <li>{@code ReactiveCrudRepository} (reactive) — returns {@link Mono} or {@link Flux}
 *       types which are lazy, non-blocking publishers. The calling thread is released
 *       immediately; the reactive MongoDB driver invokes a callback when the response arrives.</li>
 * </ul>
 *
 * <p><strong>How Spring Data generates implementations:</strong>
 * <ol>
 *   <li><em>Derived queries</em> — Spring Data parses the method name to build a MongoDB
 *       query. For example, {@code findByAuthor(String author)} generates a query that
 *       matches documents where the {@code author} field equals the parameter.</li>
 *   <li><em>{@link Query} annotations</em> — raw MongoDB JSON queries for complex
 *       expressions not expressible as method names. Uses MongoDB query syntax
 *       (not SQL), e.g., {@code { "price": { "$gte": ?0, "$lte": ?1 } }}.</li>
 * </ol>
 *
 * <p>Generic type parameters: {@code <Book, String>}
 * <ul>
 *   <li>First – the entity type managed by this repository.</li>
 *   <li>Second – the type of the entity's {@code @Id} field (MongoDB ObjectId as String).</li>
 * </ul>
 */
@Repository
public interface BookRepository extends ReactiveCrudRepository<Book, String> {

    /**
     * Find all books by a given author (exact, case-sensitive match).
     *
     * <p>Spring Data derives the MongoDB query:
     * {@code { "author": author }} from the method name {@code findByAuthor}.
     * Returns a {@link Flux} because one author may have written multiple books.
     *
     * @param author the author's full name to search for
     * @return a Flux emitting matching books, completing empty if none found
     */
    Flux<Book> findByAuthor(String author);

    /**
     * Find a book by its unique ISBN.
     *
     * <p>Since ISBN is unique (enforced by the MongoDB unique index), this returns
     * a {@link Mono} — at most one document can match.
     * Derived query: {@code { "isbn": isbn }}.
     *
     * @param isbn the ISBN to search for
     * @return a Mono emitting the book if found, or empty if not found
     */
    Mono<Book> findByIsbn(String isbn);

    /**
     * Find all books that are available (visible on the storefront).
     *
     * <p>Derived query: {@code { "available": available }}.
     * Pass {@code true} for the public storefront; {@code false} for the admin
     * view of unavailable books.
     *
     * @param available {@code true} to return available books; {@code false} for unavailable
     * @return a Flux emitting books matching the given availability status
     */
    Flux<Book> findByAvailable(boolean available);

    /**
     * Find all books published in a given year.
     *
     * <p>Derived query: {@code { "published_year": year }}.
     *
     * @param year the four-digit publication year (e.g., 1984)
     * @return a Flux emitting books published in that year
     */
    Flux<Book> findByPublishedYear(int year);

    /**
     * Find all books whose price falls within an inclusive range [min, max].
     *
     * <p>Uses Spring Data's derived query convention: {@code findByPriceBetween(min, max)}
     * generates a MongoDB query with {@code $gte} / {@code $lte} operators. Using a
     * derived method (rather than a {@link Query} annotation) ensures that Spring Data's
     * type mapping converts the Java {@link BigDecimal} to a BSON {@code Decimal128}
     * before sending it to MongoDB. A raw {@code @Query} string would pass the value as a
     * BSON string, causing incorrect lexicographic comparisons (e.g., "5.00" > "15.00"
     * alphabetically, which would be a wrong result).
     *
     * @param min the minimum price (inclusive)
     * @param max the maximum price (inclusive)
     * @return a Flux emitting books within the price range
     */
    Flux<Book> findByPriceBetween(Double min, Double max);

    /**
     * Find all books whose title contains the given keyword (case-insensitive).
     *
     * <p>Uses MongoDB's {@code $regex} operator with the {@code 'i'} (case-insensitive)
     * option. The keyword is wrapped with {@code .*} wildcards so the match is a
     * substring search anywhere in the title.
     *
     * <p>Note: Unlike ILIKE in PostgreSQL, MongoDB regex searches can be slow on
     * large collections without a text index. For production use with large datasets,
     * consider using MongoDB's full-text search ({@code $text} operator with a
     * text index instead.
     *
     * @param keyword text to search for within book titles (case-insensitive)
     * @return a Flux emitting books whose title contains the keyword
     */
    @Query("{ 'title': { '$regex': ?0, '$options': 'i' } }")
    Flux<Book> findByTitleContaining(String keyword);

    /**
     * Find all books that belong to a given genre.
     *
     * <p>MongoDB's array query behaviour: when you query a field that contains an array
     * (like {@code genres}), the {@code $eq} (equality) operator checks whether the
     * array contains the specified value. So {@code { "genres": "fiction" }} matches
     * documents where the {@code genres} array includes the string "fiction".
     *
     * <p>Derived query: {@code { "genres": genre }}.
     *
     * @param genre the genre to search for (e.g., "fiction", "dystopia")
     * @return a Flux emitting books that include the given genre in their genres array
     */
    Flux<Book> findByGenres(String genre);

    /**
     * Count the total number of books by a given author.
     *
     * <p>Aggregates without loading all documents into memory.
     * Returns a {@link Mono} because a count is always a single value.
     * Derived query: {@code { "author": author }}.
     *
     * @param author the author's full name
     * @return a Mono emitting the count of books by that author
     */
    Mono<Long> countByAuthor(String author);

    /**
     * Check whether a book with the given ISBN already exists in the collection.
     *
     * <p>Used for duplicate-check logic before inserting a new book.
     * Derived query: uses Spring Data's {@code existsBy} convention.
     *
     * @param isbn the ISBN to check
     * @return a Mono emitting {@code true} if a document with that ISBN exists,
     *         {@code false} otherwise
     */
    Mono<Boolean> existsByIsbn(String isbn);
}
