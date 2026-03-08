package com.example.exceptiontranslators.repository;

import com.example.exceptiontranslators.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Repository interface for Book entity.
 * Extends JpaRepository to provide standard CRUD operations.
 * Spring Data JPA automatically implements this interface at runtime.
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Finds a book by its ISBN.
     *
     * @param isbn The ISBN to search for.
     * @return An Optional containing the Book if found, or empty otherwise.
     */
    Optional<Book> findByIsbn(String isbn);

    /**
     * Checks if a book exists with the given ISBN.
     *
     * @param isbn The ISBN to check.
     * @return true if a book with the ISBN exists, false otherwise.
     */
    boolean existsByIsbn(String isbn);
}
