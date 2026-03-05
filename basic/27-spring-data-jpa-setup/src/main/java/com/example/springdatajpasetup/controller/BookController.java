package com.example.springdatajpasetup.controller;

import com.example.springdatajpasetup.model.Book;
import com.example.springdatajpasetup.service.BookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST Controller responsible for exposing the API endpoints.
 * 
 * @RestController is equivalent to @Controller + @ResponseBody.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    // Dependency injection via constructor
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /**
     * Endpoint to retrieve all books.
     * Usage: GET /api/books
     */
    @GetMapping
    public List<Book> getAllBooks() {
        return bookService.getAllBooks();
    }

    /**
     * Endpoint to retrieve a specific book by its ID.
     * Usage: GET /api/books/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        Optional<Book> book = bookService.getBookById(id);
        return book.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Endpoint to create a new book.
     * Usage: POST /api/books
     */
    @PostMapping
    public ResponseEntity<Book> createBook(@RequestBody Book book) {
        Book savedBook = bookService.saveBook(book);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedBook);
    }

    /**
     * Endpoint to delete a book by its ID.
     * Usage: DELETE /api/books/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }
}
