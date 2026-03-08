package com.example.exceptiontranslators.service;

import com.example.exceptiontranslators.entity.Book;
import com.example.exceptiontranslators.exception.BookAlreadyExistsException;
import com.example.exceptiontranslators.exception.BookNotFoundException;
import com.example.exceptiontranslators.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service class containing business logic for Book operations.
 * It interacts with the repository and throws custom exceptions when business rules are violated.
 */
@Service
public class BookService {

    private final BookRepository bookRepository;

    /**
     * Constructor injection for the repository.
     *
     * @param bookRepository The BookRepository instance.
     */
    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    /**
     * Creates a new book.
     *
     * @param book The book to create.
     * @return The created book.
     * @throws BookAlreadyExistsException if a book with the same ISBN already exists.
     */
    public Book createBook(Book book) {
        if (bookRepository.existsByIsbn(book.getIsbn())) {
            throw new BookAlreadyExistsException("Book with ISBN " + book.getIsbn() + " already exists");
        }
        return bookRepository.save(book);
    }

    /**
     * Retrieves a book by its ISBN.
     *
     * @param isbn The ISBN of the book.
     * @return The found book.
     * @throws BookNotFoundException if no book is found with the given ISBN.
     */
    public Book getBookByIsbn(String isbn) {
        return bookRepository.findByIsbn(isbn)
                .orElseThrow(() -> new BookNotFoundException("Book with ISBN " + isbn + " not found"));
    }

    /**
     * Retrieves all books.
     *
     * @return A list of all books.
     */
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }
}
