package com.example.jdbctemplate.repository;

import com.example.jdbctemplate.domain.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BookRepository using @JdbcTest.
 * This will configure an in-memory database and evaluate JdbcTemplate
 * operations.
 */
@JdbcTest
@Import(BookRepository.class) // Import the repository bean so it gets injected
public class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Test
    void shouldSaveAndFindBook() {
        Book newBook = new Book(null, "Integration Testing", "Test Author", 2023);
        Book savedBook = bookRepository.save(newBook);

        assertNotNull(savedBook.getId(), "Saved book should have an auto-generated ID");

        Optional<Book> foundBook = bookRepository.findById(savedBook.getId());
        assertTrue(foundBook.isPresent());
        assertEquals("Integration Testing", foundBook.get().getTitle());
    }

    @Test
    void shouldReturnAllBooks() {
        // Saving a couple of books first
        bookRepository.save(new Book(null, "Book 1", "Author 1", 2000));
        bookRepository.save(new Book(null, "Book 2", "Author 2", 2001));

        List<Book> books = bookRepository.findAll();
        assertTrue(books.size() >= 2);
    }

    @Test
    void shouldUpdateBook() {
        Book savedBook = bookRepository.save(new Book(null, "Old Title", "Author", 2000));

        savedBook.setTitle("Updated Title");
        int rowsAffected = bookRepository.update(savedBook);
        assertEquals(1, rowsAffected);

        Optional<Book> updatedBook = bookRepository.findById(savedBook.getId());
        assertEquals("Updated Title", updatedBook.get().getTitle());
    }

    @Test
    void shouldDeleteBook() {
        Book savedBook = bookRepository.save(new Book(null, "To Be Deleted", "Author", 2000));

        int rowsAffected = bookRepository.deleteById(savedBook.getId());
        assertEquals(1, rowsAffected);

        Optional<Book> deletedBook = bookRepository.findById(savedBook.getId());
        assertFalse(deletedBook.isPresent());
    }
}
