package com.example.dockercomposesupport.controller;

import com.example.dockercomposesupport.dto.BookRequest;
import com.example.dockercomposesupport.dto.BookResponse;
import com.example.dockercomposesupport.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the book catalogue API.
 *
 * <h2>URL prefix</h2>
 * <p>All endpoints are served under {@code /api/books}.</p>
 *
 * <h2>Endpoint summary</h2>
 * <pre>
 *   POST   /api/books              — create a new book
 *   GET    /api/books              — list all books (optional: ?author=X or ?year=Y or ?q=keyword)
 *   GET    /api/books/{id}         — get a single book by ID
 *   PUT    /api/books/{id}         — update an existing book
 *   DELETE /api/books/{id}         — delete a book
 * </pre>
 *
 * <h2>Validation</h2>
 * <p>{@code @Valid} on request body parameters triggers Bean Validation on the
 * {@link BookRequest} DTO. Constraint violations are caught by
 * {@link com.example.dockercomposesupport.exception.GlobalExceptionHandler} and
 * returned as HTTP 400 responses.</p>
 *
 * <h2>Statelessness</h2>
 * <p>The controller itself holds no state — all business logic lives in
 * {@link BookService}. The controller's sole responsibility is to translate
 * HTTP concerns (path variables, query params, status codes) into service calls.</p>
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    /** Service layer that handles business logic. */
    private final BookService bookService;

    /**
     * Constructor injection — makes the dependency explicit and testable
     * without a Spring context.
     *
     * @param bookService the book catalogue service
     */
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    // =========================================================================
    // POST /api/books
    // =========================================================================

    /**
     * Creates a new book in the catalogue.
     *
     * <p>{@code @Valid} causes Spring to run Bean Validation on the request body
     * before the method is invoked. If validation fails, a 400 Bad Request is
     * returned automatically by the global exception handler.</p>
     *
     * @param request the validated request body
     * @return HTTP 201 Created with the persisted book in the response body
     */
    @PostMapping
    public ResponseEntity<BookResponse> createBook(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.createBook(request);
        // HTTP 201 Created is more semantically correct than 200 OK for resource creation
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // =========================================================================
    // GET /api/books
    // =========================================================================

    /**
     * Lists books, with optional query-based filtering.
     *
     * <p>Query parameters are mutually exclusive; the first present one wins:</p>
     * <ol>
     *   <li>{@code author} — filters by author name (partial, case-insensitive)</li>
     *   <li>{@code year} — filters by publication year</li>
     *   <li>{@code q} — full-text keyword search across title, author, description</li>
     *   <li>(none) — returns all books</li>
     * </ol>
     *
     * @param author   optional author filter
     * @param year     optional publication year filter
     * @param keyword  optional keyword for full-text search
     * @return HTTP 200 OK with the list of matching books
     */
    @GetMapping
    public ResponseEntity<List<BookResponse>> listBooks(
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Integer year,
            @RequestParam(name = "q", required = false) String keyword) {

        List<BookResponse> books;

        if (author != null && !author.isBlank()) {
            // Filter by author name
            books = bookService.findByAuthor(author);
        } else if (year != null) {
            // Filter by publication year
            books = bookService.findByYear(year);
        } else if (keyword != null && !keyword.isBlank()) {
            // Keyword search across title / author / description
            books = bookService.search(keyword);
        } else {
            // No filter — return everything
            books = bookService.getAllBooks();
        }

        return ResponseEntity.ok(books);
    }

    // =========================================================================
    // GET /api/books/{id}
    // =========================================================================

    /**
     * Returns a single book by its database ID.
     *
     * @param id the book's primary key (from the URL path)
     * @return HTTP 200 OK with the book, or HTTP 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> getBook(@PathVariable Long id) {
        return ResponseEntity.ok(bookService.getBookById(id));
    }

    // =========================================================================
    // PUT /api/books/{id}
    // =========================================================================

    /**
     * Replaces all fields of an existing book with the values from the request body.
     *
     * <p>This is a full-replacement (PUT semantics), not a partial update (PATCH).
     * All required fields must be supplied even if only one changes.</p>
     *
     * @param id      the book's primary key (from the URL path)
     * @param request the validated request body with the new values
     * @return HTTP 200 OK with the updated book, or HTTP 404 / 409 on error
     */
    @PutMapping("/{id}")
    public ResponseEntity<BookResponse> updateBook(
            @PathVariable Long id,
            @Valid @RequestBody BookRequest request) {
        return ResponseEntity.ok(bookService.updateBook(id, request));
    }

    // =========================================================================
    // DELETE /api/books/{id}
    // =========================================================================

    /**
     * Deletes a book from the catalogue.
     *
     * @param id the book's primary key (from the URL path)
     * @return HTTP 204 No Content on success, or HTTP 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        // HTTP 204 No Content — the resource has been removed; no body to return
        return ResponseEntity.noContent().build();
    }
}
