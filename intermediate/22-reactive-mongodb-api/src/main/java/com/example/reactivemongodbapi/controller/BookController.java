package com.example.reactivemongodbapi.controller;

import com.example.reactivemongodbapi.domain.Book;
import com.example.reactivemongodbapi.dto.BookRequest;
import com.example.reactivemongodbapi.service.BookService;
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
 * REST controller exposing non-blocking HTTP endpoints for book management.
 *
 * <p>Annotations:
 * <ul>
 *   <li>{@code @RestController} – combines {@code @Controller} and {@code @ResponseBody}.
 *       Every return value is automatically serialised to JSON by Jackson.</li>
 *   <li>{@code @RequestMapping("/api/books")} – all endpoints in this class are
 *       prefixed with {@code /api/books}.</li>
 * </ul>
 *
 * <p><strong>How WebFlux handles reactive return types:</strong><br>
 * Spring WebFlux subscribes to the returned {@link Mono} or {@link Flux} and writes
 * the emitted items to the HTTP response asynchronously. The calling thread is never
 * blocked waiting for MongoDB — it handles other requests while the I/O completes.
 *
 * <p><strong>Controller responsibilities:</strong>
 * <ul>
 *   <li>Parse and validate HTTP request data ({@code @Valid}, {@code @PathVariable}, etc.).</li>
 *   <li>Delegate all business logic to {@link BookService}.</li>
 *   <li>Map empty Monos to HTTP 404 via {@code switchIfEmpty}.</li>
 *   <li>Set appropriate HTTP status codes ({@code @ResponseStatus}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    /**
     * Constructor injection — makes the dependency explicit and testable.
     *
     * @param bookService the service containing book business logic
     */
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    // ── GET /api/books ────────────────────────────────────────────────────────────

    /**
     * List all books.
     *
     * <p>Returns a {@link Flux} that streams each book as JSON. WebFlux writes each
     * MongoDB document to the response body as it arrives — no need to buffer all
     * documents in memory.
     *
     * @return a Flux emitting all books
     */
    @GetMapping
    public Flux<Book> getAllBooks() {
        return bookService.findAll();
    }

    // ── GET /api/books/{id} ───────────────────────────────────────────────────────

    /**
     * Get a single book by its MongoDB ObjectId.
     *
     * <p>{@code switchIfEmpty} transforms an empty {@link Mono} (book not found)
     * into a {@link Mono} that emits an error, which WebFlux converts to HTTP 404.
     *
     * @param id the MongoDB ObjectId string (24 hex chars)
     * @return a Mono emitting the book, or 404 if not found
     */
    @GetMapping("/{id}")
    public Mono<Book> getBookById(@PathVariable String id) {
        return bookService.findById(id)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found: " + id)));
    }

    // ── GET /api/books/isbn/{isbn} ────────────────────────────────────────────────

    /**
     * Get a single book by its ISBN.
     *
     * <p>ISBN is unique in the collection, so at most one document matches.
     *
     * @param isbn the book's ISBN
     * @return a Mono emitting the book, or 404 if not found
     */
    @GetMapping("/isbn/{isbn}")
    public Mono<Book> getBookByIsbn(@PathVariable String isbn) {
        return bookService.findByIsbn(isbn)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ISBN: " + isbn)));
    }

    // ── GET /api/books/author/{author} ────────────────────────────────────────────

    /**
     * List all books by a given author (exact match).
     *
     * @param author the author's full name (path variable, URL-encoded)
     * @return a Flux emitting books by that author
     */
    @GetMapping("/author/{author}")
    public Flux<Book> getBooksByAuthor(@PathVariable String author) {
        return bookService.findByAuthor(author);
    }

    // ── GET /api/books/available ──────────────────────────────────────────────────

    /**
     * List all available books (visible on the storefront).
     *
     * @return a Flux emitting only available books
     */
    @GetMapping("/available")
    public Flux<Book> getAvailableBooks() {
        return bookService.findAvailable();
    }

    // ── GET /api/books/year/{year} ────────────────────────────────────────────────

    /**
     * List all books published in a given year.
     *
     * @param year the four-digit publication year (e.g., 1984)
     * @return a Flux emitting books published in that year
     */
    @GetMapping("/year/{year}")
    public Flux<Book> getBooksByYear(@PathVariable int year) {
        return bookService.findByYear(year);
    }

    // ── GET /api/books/genre/{genre} ──────────────────────────────────────────────

    /**
     * List all books that belong to a given genre.
     *
     * <p>MongoDB queries the {@code genres} array field — no join required.
     *
     * @param genre the genre tag to filter by (e.g., "fiction", "dystopia")
     * @return a Flux emitting books with that genre
     */
    @GetMapping("/genre/{genre}")
    public Flux<Book> getBooksByGenre(@PathVariable String genre) {
        return bookService.findByGenre(genre);
    }

    // ── GET /api/books/search?keyword=... ─────────────────────────────────────────

    /**
     * Search books by keyword in their title (case-insensitive substring match).
     *
     * <p>The {@code keyword} query parameter is required — Spring returns 400 if missing.
     *
     * @param keyword text to search for within book titles
     * @return a Flux emitting matching books
     */
    @GetMapping("/search")
    public Flux<Book> searchByTitle(@RequestParam String keyword) {
        return bookService.searchByTitle(keyword);
    }

    // ── GET /api/books/price-range?min=...&max=... ────────────────────────────────

    /**
     * List all books within a price range.
     *
     * <p>Both {@code min} and {@code max} are required query parameters.
     *
     * @param min minimum price (inclusive)
     * @param max maximum price (inclusive)
     * @return a Flux emitting books in the price range
     */
    @GetMapping("/price-range")
    public Flux<Book> getBooksByPriceRange(@RequestParam Double min,
                                           @RequestParam Double max) {
        return bookService.findByPriceRange(min, max);
    }

    // ── GET /api/books/author/{author}/count ──────────────────────────────────────

    /**
     * Count how many books exist by a given author.
     *
     * <p>Returns a {@link Mono}{@code <Long>} serialised as a plain JSON number.
     *
     * @param author the author's full name
     * @return a Mono emitting the count
     */
    @GetMapping("/author/{author}/count")
    public Mono<Long> countByAuthor(@PathVariable String author) {
        return bookService.countByAuthor(author);
    }

    // ── POST /api/books ───────────────────────────────────────────────────────────

    /**
     * Create a new book.
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body. If any constraint
     * is violated, Spring WebFlux returns HTTP 400 Bad Request before this method is invoked.
     *
     * <p>{@code @ResponseStatus(CREATED)} changes the default 200 status to 201.
     *
     * @param request the book data from the request body
     * @return a Mono emitting the created book with its generated MongoDB ObjectId
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Book> createBook(@Valid @RequestBody BookRequest request) {
        return bookService.create(request);
    }

    // ── PUT /api/books/{id} ───────────────────────────────────────────────────────

    /**
     * Update an existing book (full replacement — PUT semantics).
     *
     * <p>If the book is not found, the service returns an empty {@link Mono}.
     * {@code switchIfEmpty} converts this to a 404 error.
     *
     * @param id      the MongoDB ObjectId string
     * @param request the new field values
     * @return a Mono emitting the updated book, or 404 if not found
     */
    @PutMapping("/{id}")
    public Mono<Book> updateBook(@PathVariable String id,
                                 @Valid @RequestBody BookRequest request) {
        return bookService.update(id, request)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found: " + id)));
    }

    // ── DELETE /api/books/{id} ────────────────────────────────────────────────────

    /**
     * Delete a book by its MongoDB ObjectId.
     *
     * <p>The service returns {@code Mono<Boolean>}:
     * <ul>
     *   <li>{@code true} — book existed and was deleted → HTTP 204 No Content.</li>
     *   <li>{@code false} — book was not found → HTTP 404 Not Found.</li>
     * </ul>
     *
     * @param id the MongoDB ObjectId string
     * @return an empty Mono (HTTP 204) or a 404 error
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteBook(@PathVariable String id) {
        return bookService.deleteById(id)
                .flatMap(deleted -> {
                    if (deleted) {
                        // Book was deleted — return empty Mono (HTTP 204 No Content)
                        return Mono.<Void>empty();
                    } else {
                        // Book was not found — signal HTTP 404
                        return Mono.error(
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found: " + id));
                    }
                });
    }
}
