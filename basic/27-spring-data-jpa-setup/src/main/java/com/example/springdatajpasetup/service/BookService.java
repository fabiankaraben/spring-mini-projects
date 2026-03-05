package com.example.springdatajpasetup.service;

import com.example.springdatajpasetup.model.Book;
import com.example.springdatajpasetup.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service class that acts as a bridge between the Controller and the
 * Repository.
 * It encapsulates the business logic.
 */
@Service
public class BookService {

    private final BookRepository bookRepository;

    // Constructor-based dependency injection is the recommended approach in Spring
    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    /**
     * Retrieves all books from the database.
     * 
     * @return List of books
     */
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    /**
     * Retrieves a book by its ID.
     * 
     * @param id The ID of the book
     * @return Optional containing the book if found, or empty otherwise
     */
    public Optional<Book> getBookById(Long id) {
        return bookRepository.findById(id);
    }

    /**
     * Saves a new book or updates an existing one on the database.
     * 
     * @param book The book object to save
     * @return The saved book
     */
    public Book saveBook(Book book) {
        return bookRepository.save(book);
    }

    /**
     * Deletes a book by its ID.
     * 
     * @param id The ID of the book to delete
     */
    public void deleteBook(Long id) {
        bookRepository.deleteById(id);
    }
}
