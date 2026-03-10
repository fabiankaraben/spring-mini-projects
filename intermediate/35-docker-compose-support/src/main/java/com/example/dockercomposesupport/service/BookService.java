package com.example.dockercomposesupport.service;

import com.example.dockercomposesupport.domain.Book;
import com.example.dockercomposesupport.dto.BookRequest;
import com.example.dockercomposesupport.dto.BookResponse;
import com.example.dockercomposesupport.exception.BookNotFoundException;
import com.example.dockercomposesupport.exception.DuplicateBookException;
import com.example.dockercomposesupport.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the book catalogue.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Enforce uniqueness invariants (title, ISBN) before persisting.</li>
 *   <li>Map between DTOs and domain entities.</li>
 *   <li>Delegate persistence to {@link BookRepository}.</li>
 *   <li>Throw domain-specific exceptions that the controller layer
 *       (via {@link com.example.dockercomposesupport.exception.GlobalExceptionHandler})
 *       converts into appropriate HTTP responses.</li>
 * </ul>
 *
 * <h2>Transaction management</h2>
 * <p>Methods are annotated with {@code @Transactional} to ensure that all
 * database operations within a single method call either commit or roll back
 * together. Read-only operations use {@code readOnly = true} as a performance
 * hint to the database driver and Hibernate (no dirty checking, no flush).</p>
 */
@Service
public class BookService {

    /** Repository for CRUD operations on Book entities. */
    private final BookRepository bookRepository;

    /**
     * Constructor injection — preferred over field injection because it makes
     * dependencies explicit and simplifies unit testing (no Spring context needed).
     *
     * @param bookRepository the JPA repository for Book entities
     */
    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    // =========================================================================
    // Create
    // =========================================================================

    /**
     * Adds a new book to the catalogue.
     *
     * <p>Before saving, uniqueness of both title and ISBN is verified.
     * If either is already taken, a {@link DuplicateBookException} is thrown
     * (which maps to HTTP 409 Conflict).</p>
     *
     * @param request the incoming request DTO (validated by the controller)
     * @return the persisted book as a response DTO
     * @throws DuplicateBookException if the title or ISBN already exists
     */
    @Transactional
    public BookResponse createBook(BookRequest request) {
        // Guard: ensure title uniqueness
        if (bookRepository.existsByTitle(request.title())) {
            throw new DuplicateBookException(
                    "Book with title '" + request.title() + "' already exists");
        }

        // Guard: ensure ISBN uniqueness
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateBookException(
                    "Book with ISBN '" + request.isbn() + "' already exists");
        }

        // Map request DTO → domain entity
        Book book = new Book(
                request.title(),
                request.author(),
                request.isbn(),
                request.publicationYear(),
                request.description()
        );

        // Persist and map entity → response DTO
        return BookResponse.from(bookRepository.save(book));
    }

    // =========================================================================
    // Read
    // =========================================================================

    /**
     * Returns all books in the catalogue, ordered by their auto-generated ID.
     *
     * @return list of all books as response DTOs
     */
    @Transactional(readOnly = true)
    public List<BookResponse> getAllBooks() {
        return bookRepository.findAll()
                .stream()
                .map(BookResponse::from)
                .toList();
    }

    /**
     * Returns a single book by its primary key.
     *
     * @param id the book's database ID
     * @return the book as a response DTO
     * @throws BookNotFoundException if no book with that ID exists
     */
    @Transactional(readOnly = true)
    public BookResponse getBookById(Long id) {
        return bookRepository.findById(id)
                .map(BookResponse::from)
                .orElseThrow(() -> new BookNotFoundException(
                        "Book with id " + id + " not found"));
    }

    /**
     * Returns all books whose author name contains the given string (case-insensitive).
     *
     * @param author partial or full author name to search for
     * @return list of matching books (may be empty)
     */
    @Transactional(readOnly = true)
    public List<BookResponse> findByAuthor(String author) {
        return bookRepository.findByAuthorContainingIgnoreCase(author)
                .stream()
                .map(BookResponse::from)
                .toList();
    }

    /**
     * Returns all books published in a specific year.
     *
     * @param year the publication year to filter by
     * @return list of books published that year (may be empty)
     */
    @Transactional(readOnly = true)
    public List<BookResponse> findByYear(Integer year) {
        return bookRepository.findByPublicationYear(year)
                .stream()
                .map(BookResponse::from)
                .toList();
    }

    /**
     * Performs a keyword search across title, author and description fields.
     *
     * @param keyword the search term (partial matches are included)
     * @return list of matching books (may be empty)
     */
    @Transactional(readOnly = true)
    public List<BookResponse> search(String keyword) {
        return bookRepository.searchByKeyword(keyword)
                .stream()
                .map(BookResponse::from)
                .toList();
    }

    // =========================================================================
    // Update
    // =========================================================================

    /**
     * Updates an existing book's fields.
     *
     * <p>All request fields replace the current values. When title or ISBN changes,
     * uniqueness is re-checked against other books (excluding the book being updated).</p>
     *
     * @param id      the ID of the book to update
     * @param request the new values
     * @return the updated book as a response DTO
     * @throws BookNotFoundException  if no book with that ID exists
     * @throws DuplicateBookException if the new title or ISBN is already taken by another book
     */
    @Transactional
    public BookResponse updateBook(Long id, BookRequest request) {
        // Load existing entity — throws if not found
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(
                        "Book with id " + id + " not found"));

        // Check title uniqueness only if title is actually changing
        if (!book.getTitle().equals(request.title())
                && bookRepository.existsByTitle(request.title())) {
            throw new DuplicateBookException(
                    "Book with title '" + request.title() + "' already exists");
        }

        // Check ISBN uniqueness only if ISBN is actually changing
        if (!book.getIsbn().equals(request.isbn())
                && bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateBookException(
                    "Book with ISBN '" + request.isbn() + "' already exists");
        }

        // Apply the new values
        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setIsbn(request.isbn());
        book.setPublicationYear(request.publicationYear());
        book.setDescription(request.description());

        // Save (Hibernate's dirty-checking would also flush automatically
        // at the end of the transaction, but explicit save makes intent clear)
        return BookResponse.from(bookRepository.save(book));
    }

    // =========================================================================
    // Delete
    // =========================================================================

    /**
     * Removes a book from the catalogue.
     *
     * @param id the ID of the book to delete
     * @throws BookNotFoundException if no book with that ID exists
     */
    @Transactional
    public void deleteBook(Long id) {
        // Verify existence before deleting to return a meaningful error if missing
        if (!bookRepository.existsById(id)) {
            throw new BookNotFoundException("Book with id " + id + " not found");
        }
        bookRepository.deleteById(id);
    }
}
