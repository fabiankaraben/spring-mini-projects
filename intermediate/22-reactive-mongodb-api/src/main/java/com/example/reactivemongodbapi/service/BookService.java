package com.example.reactivemongodbapi.service;

import com.example.reactivemongodbapi.domain.Book;
import com.example.reactivemongodbapi.dto.BookRequest;
import com.example.reactivemongodbapi.repository.BookRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


/**
 * Service layer containing the business logic for book management.
 *
 * <p>This class sits between the HTTP layer ({@link com.example.reactivemongodbapi.controller.BookController})
 * and the data access layer ({@link BookRepository}). Key responsibilities:
 * <ul>
 *   <li>Map request DTOs ({@link BookRequest}) to domain entities ({@link Book}).</li>
 *   <li>Orchestrate reactive streams using {@link Mono} and {@link Flux} operators.</li>
 *   <li>Encapsulate business rules (e.g., duplicate ISBN detection, preserving
 *       {@code createdAt} on update, building regex patterns for title search).</li>
 * </ul>
 *
 * <p><strong>Reactive programming model:</strong><br>
 * Every method returns a {@link Mono} (0 or 1 item) or a {@link Flux} (0..N items).
 * These are <em>lazy cold publishers</em> — no MongoDB query is executed until a
 * subscriber subscribes. The WebFlux controller subscribes when it serialises the
 * HTTP response. This means no query is issued if, for example, the route is never hit.
 *
 * <p><strong>Key Reactive MongoDB vs blocking MongoDB behaviours to understand:</strong>
 * <ul>
 *   <li>Reactive MongoDB {@code save()} detects a {@code null} {@code @Id}: if {@code null}
 *       → INSERT (MongoDB generates an ObjectId); if non-null → UPDATE (upsert-style).
 *       Unlike JPA, there is no persistent context or dirty checking.</li>
 *   <li>There are no lazy-loaded relationships in MongoDB / Spring Data Reactive MongoDB —
 *       related documents must be fetched with separate queries.</li>
 *   <li>All operations return {@code Publisher} types ({@link Mono} / {@link Flux}),
 *       keeping the entire stack non-blocking from controller down to the wire.</li>
 * </ul>
 *
 * <p><strong>Operator glossary used in this class:</strong>
 * <ul>
 *   <li>{@code flatMap} – transform each item into a new publisher and flatten results.</li>
 *   <li>{@code map} – transform each item synchronously (no new publisher).</li>
 *   <li>{@code switchIfEmpty} – emit a fallback publisher when the upstream is empty.</li>
 *   <li>{@code thenReturn} – discard an upstream signal and emit a fixed value instead.</li>
 *   <li>{@code error} – create a publisher that immediately signals an error.</li>
 * </ul>
 */
@Service
public class BookService {

    private final BookRepository bookRepository;

    /**
     * Constructor injection makes the dependency explicit and enables unit testing
     * without a Spring context (just pass a mock repository).
     *
     * @param bookRepository reactive MongoDB repository for books
     */
    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    // ── Read operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all books as a reactive stream.
     *
     * <p>Returns a {@link Flux} that emits each MongoDB document as it arrives —
     * back-pressure is automatically handled so the application never loads the entire
     * collection into heap memory at once.
     *
     * @return a Flux emitting all books
     */
    public Flux<Book> findAll() {
        return bookRepository.findAll();
    }

    /**
     * Retrieve a single book by its MongoDB ObjectId.
     *
     * <p>Returns a {@link Mono} that either emits the book or completes empty.
     * The controller maps an empty Mono to HTTP 404.
     *
     * @param id the MongoDB ObjectId string (24 hex chars)
     * @return a Mono emitting the book, or empty if not found
     */
    public Mono<Book> findById(String id) {
        return bookRepository.findById(id);
    }

    /**
     * Retrieve a single book by its unique ISBN.
     *
     * <p>Returns a {@link Mono} because ISBN is unique — at most one document matches.
     *
     * @param isbn the book's ISBN
     * @return a Mono emitting the book, or empty if not found
     */
    public Mono<Book> findByIsbn(String isbn) {
        return bookRepository.findByIsbn(isbn);
    }

    /**
     * Find all books by a given author (exact match).
     *
     * @param author the author's full name
     * @return a Flux emitting all books by that author
     */
    public Flux<Book> findByAuthor(String author) {
        return bookRepository.findByAuthor(author);
    }

    /**
     * Find all available books (visible on the storefront).
     *
     * @return a Flux emitting only available books
     */
    public Flux<Book> findAvailable() {
        return bookRepository.findByAvailable(true);
    }

    /**
     * Find all books published in a given year.
     *
     * @param year the four-digit publication year
     * @return a Flux emitting books published in that year
     */
    public Flux<Book> findByYear(int year) {
        return bookRepository.findByPublishedYear(year);
    }

    /**
     * Find all books whose price falls within an inclusive range [min, max].
     *
     * @param min the minimum price (inclusive)
     * @param max the maximum price (inclusive)
     * @return a Flux emitting matching books
     */
    public Flux<Book> findByPriceRange(Double min, Double max) {
        return bookRepository.findByPriceBetween(min, max);
    }

    /**
     * Search books by keyword in their title (case-insensitive substring match).
     *
     * <p>The keyword is passed directly to the repository which uses MongoDB's
     * {@code $regex} operator with the {@code 'i'} (case-insensitive) option.
     * Unlike SQL LIKE, MongoDB regex does not need {@code %} wildcards — a plain
     * keyword is already a substring regex pattern.
     *
     * @param keyword text to search for within book titles
     * @return a Flux emitting books whose titles contain the keyword
     */
    public Flux<Book> searchByTitle(String keyword) {
        // Pass the keyword as a regex pattern; the repository applies case-insensitive matching.
        // MongoDB's $regex with 'i' option handles case-insensitivity natively.
        return bookRepository.findByTitleContaining(keyword);
    }

    /**
     * Find all books that belong to a given genre.
     *
     * <p>MongoDB queries an array field (genres) to find documents where the array
     * contains the specified genre value. No join table or special syntax is needed.
     *
     * @param genre the genre to filter by (e.g., "fiction", "dystopia")
     * @return a Flux emitting books that include the given genre
     */
    public Flux<Book> findByGenre(String genre) {
        return bookRepository.findByGenres(genre);
    }

    /**
     * Count the total number of books by a given author.
     *
     * @param author the author's full name
     * @return a Mono emitting the count
     */
    public Mono<Long> countByAuthor(String author) {
        return bookRepository.countByAuthor(author);
    }

    // ── Write operations ──────────────────────────────────────────────────────────

    /**
     * Create and persist a new book document in MongoDB.
     *
     * <p>First checks whether a book with the same ISBN already exists. If so,
     * the reactive pipeline signals an {@link IllegalStateException} which the
     * global exception handler converts to HTTP 409 Conflict.
     *
     * <p>Reactive pipeline:
     * <ol>
     *   <li>{@code existsByIsbn(isbn)} — check for duplicate ISBN.</li>
     *   <li>{@code flatMap(exists → ...)} — if duplicate, emit error; otherwise save.</li>
     *   <li>{@code save(entity)} — MongoDB generates an ObjectId and returns the document
     *       with the generated {@code _id} populated.</li>
     * </ol>
     *
     * @param request the book data from the HTTP request body
     * @return a Mono emitting the persisted book with its generated MongoDB ObjectId
     */
    public Mono<Book> create(BookRequest request) {
        // Check for duplicate ISBN before inserting
        return bookRepository.existsByIsbn(request.getIsbn())
                .flatMap(exists -> {
                    if (exists) {
                        // Signal a conflict error — the global handler maps this to HTTP 409
                        return Mono.<Book>error(
                                new IllegalStateException("A book with ISBN '" + request.getIsbn() + "' already exists"));
                    }
                    // Map DTO → domain entity; id is null so MongoDB generates an ObjectId
                    Book book = new Book(
                            request.getTitle(),
                            request.getAuthor(),
                            request.getIsbn(),
                            request.getPrice(),
                            request.getPublishedYear(),
                            request.getGenres(),
                            request.getDescription(),
                            request.getLanguage(),
                            request.getPageCount(),
                            request.isAvailable()
                    );
                    // save() detects id == null → issues INSERT; emits the saved document with id set
                    return bookRepository.save(book);
                });
    }

    /**
     * Update an existing book document (full replacement — PUT semantics).
     *
     * <p>Reactive pipeline:
     * <ol>
     *   <li>{@code findById(id)} — look up the existing document; returns empty Mono if absent.</li>
     *   <li>{@code map(...)} — mutate the entity in-place with new field values.</li>
     *   <li>{@code flatMap(save)} — persist the mutated entity; Reactive MongoDB issues an
     *       update because the id is non-null.</li>
     * </ol>
     * Returns an empty {@link Mono} if the book is not found; the controller maps this to HTTP 404.
     *
     * <p>Note: {@code createdAt} is intentionally NOT updated here. The {@code @LastModifiedDate}
     * annotation on {@code updatedAt} ensures it is refreshed automatically by Spring Data
     * auditing on every save.
     *
     * @param id      the MongoDB ObjectId string
     * @param request the new field values
     * @return a Mono emitting the updated book, or empty if not found
     */
    public Mono<Book> update(String id, BookRequest request) {
        return bookRepository.findById(id)
                // map() applies synchronous mutations — no new publisher is created here
                .map(existing -> {
                    existing.setTitle(request.getTitle());
                    existing.setAuthor(request.getAuthor());
                    existing.setIsbn(request.getIsbn());
                    existing.setPrice(request.getPrice());
                    existing.setPublishedYear(request.getPublishedYear());
                    existing.setGenres(request.getGenres());
                    existing.setDescription(request.getDescription());
                    existing.setLanguage(request.getLanguage());
                    existing.setPageCount(request.getPageCount());
                    existing.setAvailable(request.isAvailable());
                    // createdAt is left unchanged; updatedAt is refreshed by @LastModifiedDate
                    return existing;
                })
                // flatMap saves the mutated entity; emits the updated document downstream
                .flatMap(bookRepository::save);
    }

    /**
     * Delete a book document by its MongoDB ObjectId.
     *
     * <p>Reactive pipeline:
     * <ol>
     *   <li>{@code findById(id)} — verify the book exists.</li>
     *   <li>{@code flatMap(deleteById)} — delete only if found; emits {@code true}.</li>
     *   <li>{@code switchIfEmpty(Mono.just(false))} — emit {@code false} if the book
     *       was not found, so the controller can respond with HTTP 404 instead of 204.</li>
     * </ol>
     *
     * @param id the MongoDB ObjectId string
     * @return a Mono emitting {@code true} if deleted, or {@code false} if not found
     */
    public Mono<Boolean> deleteById(String id) {
        return bookRepository.findById(id)
                // Only delete if the book actually exists
                .flatMap(book -> bookRepository.deleteById(book.getId())
                        // deleteById returns Mono<Void>; thenReturn maps it to true
                        .thenReturn(true))
                // If findById was empty, emit false to signal "not found"
                .switchIfEmpty(Mono.just(false));
    }
}
