package com.example.graphqlapi.service;

import com.example.graphqlapi.domain.Author;
import com.example.graphqlapi.domain.Book;
import com.example.graphqlapi.dto.BookInput;
import com.example.graphqlapi.repository.AuthorRepository;
import com.example.graphqlapi.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for book-related business logic.
 *
 * <p>Mediates between the GraphQL resolver ({@link com.example.graphqlapi.controller.BookController})
 * and the JPA repositories ({@link BookRepository}, {@link AuthorRepository}).
 * All write operations are wrapped in transactions to guarantee data consistency.
 */
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;

    /**
     * @param bookRepository   repository for book persistence operations
     * @param authorRepository repository used to look up the author referenced in input DTOs
     */
    public BookService(BookRepository bookRepository, AuthorRepository authorRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
    }

    // ── Read operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all books from the database.
     *
     * @return list of all books (empty list if none exist)
     */
    @Transactional(readOnly = true)
    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    /**
     * Retrieve a single book by its primary key.
     *
     * @param id the book's primary key
     * @return an {@link Optional} containing the book, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<Book> findById(Long id) {
        return bookRepository.findById(id);
    }

    /**
     * Retrieve all books in a given genre.
     *
     * @param genre the genre name to filter by (case-insensitive)
     * @return list of books in that genre
     */
    @Transactional(readOnly = true)
    public List<Book> findByGenre(String genre) {
        return bookRepository.findByGenreIgnoreCase(genre);
    }

    /**
     * Retrieve all books written by a specific author.
     *
     * @param authorId the primary key of the author
     * @return list of books by that author
     */
    @Transactional(readOnly = true)
    public List<Book> findByAuthorId(Long authorId) {
        return bookRepository.findByAuthorId(authorId);
    }

    /**
     * Search books by title (case-insensitive, substring match).
     *
     * @param title the title fragment to search for
     * @return list of matching books
     */
    @Transactional(readOnly = true)
    public List<Book> searchByTitle(String title) {
        return bookRepository.findByTitleContainingIgnoreCase(title);
    }

    // ── Write operations ──────────────────────────────────────────────────────────

    /**
     * Create and persist a new book.
     *
     * <p>Looks up the author by the ID provided in the input DTO. If the author
     * does not exist, an {@link IllegalArgumentException} is thrown. GraphQL
     * exception handlers convert this into a structured GraphQL error response.
     *
     * @param input the book data from the GraphQL mutation argument
     * @return the persisted book with its generated primary key
     * @throws IllegalArgumentException if no author exists with the given {@code authorId}
     */
    @Transactional
    public Book create(BookInput input) {
        // Resolve the author reference – fail fast if the author doesn't exist
        Author author = authorRepository.findById(input.getAuthorId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Author not found with id: " + input.getAuthorId()));

        Book book = new Book(
                input.getTitle(),
                input.getIsbn(),
                input.getPublishedDate(),
                input.getGenre(),
                author
        );
        return bookRepository.save(book);
    }

    /**
     * Update an existing book's mutable fields.
     *
     * <p>If a new {@code authorId} is provided in the input, the author reference
     * is re-resolved and updated. JPA dirty-checking issues the SQL UPDATE when
     * the surrounding transaction commits.
     *
     * @param id    the primary key of the book to update
     * @param input the new field values
     * @return an {@link Optional} with the updated book, or empty if not found
     * @throws IllegalArgumentException if the new author referenced in {@code input} does not exist
     */
    @Transactional
    public Optional<Book> update(Long id, BookInput input) {
        Optional<Book> existing = bookRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Book book = existing.get();
        book.setTitle(input.getTitle());
        book.setIsbn(input.getIsbn());
        book.setPublishedDate(input.getPublishedDate());
        book.setGenre(input.getGenre());

        // Re-resolve author if a (potentially different) author ID is supplied
        Author author = authorRepository.findById(input.getAuthorId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Author not found with id: " + input.getAuthorId()));
        book.setAuthor(author);

        // No explicit save() needed – JPA dirty-checking handles the UPDATE on commit
        return Optional.of(book);
    }

    /**
     * Delete a book by primary key.
     *
     * @param id the primary key of the book to delete
     * @return {@code true} if the book existed and was deleted; {@code false} if not found
     */
    @Transactional
    public boolean deleteById(Long id) {
        if (!bookRepository.existsById(id)) {
            return false;
        }
        bookRepository.deleteById(id);
        return true;
    }
}
