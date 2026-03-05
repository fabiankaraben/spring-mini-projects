package com.example.springdatajpasetup.repository;

import com.example.springdatajpasetup.model.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sliced integration test for Repository layer using @DataJpaTest.
 * This annotation configures an in-memory database and tests the repository
 * components.
 */
@DataJpaTest
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Test
    void testSaveAndFindById() {
        // Arrange
        Book book = new Book("Effective Java", "Joshua Bloch", 45.00);

        // Act
        Book savedBook = bookRepository.save(book);
        Optional<Book> foundBook = bookRepository.findById(savedBook.getId());

        // Assert
        assertTrue(foundBook.isPresent());
        assertEquals("Effective Java", foundBook.get().getTitle());
        assertNotNull(foundBook.get().getId());
    }

    @Test
    void testFindAll() {
        // Arrange
        bookRepository.save(new Book("Clean Code", "Robert C. Martin", 40.00));
        bookRepository.save(new Book("Clean Architecture", "Robert C. Martin", 38.00));

        // Act
        List<Book> books = bookRepository.findAll();

        // Assert
        assertEquals(2, books.size());
    }
}
