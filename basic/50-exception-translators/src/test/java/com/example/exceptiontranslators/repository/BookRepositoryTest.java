package com.example.exceptiontranslators.repository;

import com.example.exceptiontranslators.entity.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class BookRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BookRepository bookRepository;

    @Test
    void findByIsbn_ShouldReturnBook_WhenItExists() {
        Book book = new Book(null, "1234567890", "Test Book", "Test Author");
        entityManager.persist(book);
        entityManager.flush();

        Optional<Book> found = bookRepository.findByIsbn("1234567890");

        assertTrue(found.isPresent());
        assertEquals("Test Book", found.get().getTitle());
    }

    @Test
    void existsByIsbn_ShouldReturnTrue_WhenItExists() {
        Book book = new Book(null, "1234567890", "Test Book", "Test Author");
        entityManager.persist(book);
        entityManager.flush();

        boolean exists = bookRepository.existsByIsbn("1234567890");

        assertTrue(exists);
    }
}
