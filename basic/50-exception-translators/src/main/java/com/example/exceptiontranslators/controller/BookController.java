package com.example.exceptiontranslators.controller;

import com.example.exceptiontranslators.entity.Book;
import com.example.exceptiontranslators.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for handling Book-related HTTP requests.
 * Exposes endpoints for creating and retrieving books.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    /**
     * Constructor injection for the service.
     *
     * @param bookService The BookService instance.
     */
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /**
     * Endpoint to create a new book.
     * Validates the request body and returns the created book with HTTP 201 Created status.
     *
     * @param book The book object to create.
     * @return A ResponseEntity containing the created book and HTTP status.
     */
    @PostMapping
    public ResponseEntity<Book> createBook(@Valid @RequestBody Book book) {
        Book createdBook = bookService.createBook(book);
        return new ResponseEntity<>(createdBook, HttpStatus.CREATED);
    }

    /**
     * Endpoint to retrieve a book by its ISBN.
     * Returns the book if found, otherwise the GlobalExceptionHandler will handle the exception.
     *
     * @param isbn The ISBN of the book to retrieve.
     * @return A ResponseEntity containing the book and HTTP 200 OK status.
     */
    @GetMapping("/{isbn}")
    public ResponseEntity<Book> getBookByIsbn(@PathVariable String isbn) {
        Book book = bookService.getBookByIsbn(isbn);
        return ResponseEntity.ok(book);
    }

    /**
     * Endpoint to retrieve all books.
     *
     * @return A ResponseEntity containing a list of all books and HTTP 200 OK status.
     */
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookService.getAllBooks());
    }
}
