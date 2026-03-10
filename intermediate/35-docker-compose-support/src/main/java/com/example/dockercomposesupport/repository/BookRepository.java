package com.example.dockercomposesupport.repository;

import com.example.dockercomposesupport.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Book} entities.
 *
 * <p>Spring Data automatically generates the implementation at runtime — no
 * boilerplate SQL or DAO classes are needed. The interface extends
 * {@link JpaRepository}, which provides standard CRUD operations plus
 * pagination and sorting support.</p>
 *
 * <h2>Custom query methods</h2>
 * <p>Methods whose names follow Spring Data's naming convention (e.g.
 * {@code findByAuthorIgnoreCase}) are parsed and translated into JPQL
 * automatically. More complex queries use {@link Query} with explicit JPQL.</p>
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Finds a book by its exact ISBN.
     *
     * @param isbn the ISBN to search for
     * @return an {@link Optional} containing the book if found, empty otherwise
     */
    Optional<Book> findByIsbn(String isbn);

    /**
     * Checks whether a book with the given title already exists.
     * Used to enforce the unique-title constraint at the service layer.
     *
     * @param title the title to check
     * @return {@code true} if a book with that title exists
     */
    boolean existsByTitle(String title);

    /**
     * Checks whether a book with the given ISBN already exists.
     * Used to enforce the unique-ISBN constraint at the service layer.
     *
     * @param isbn the ISBN to check
     * @return {@code true} if a book with that ISBN exists
     */
    boolean existsByIsbn(String isbn);

    /**
     * Finds all books whose author name contains the given string,
     * case-insensitively.
     *
     * <p>Spring Data derives the query from the method name:
     * {@code findBy} + {@code Author} + {@code ContainingIgnoreCase}.</p>
     *
     * @param author partial or full author name
     * @return list of matching books
     */
    List<Book> findByAuthorContainingIgnoreCase(String author);

    /**
     * Finds all books published in a given year.
     *
     * @param year the publication year to search
     * @return list of books published that year
     */
    List<Book> findByPublicationYear(Integer year);

    /**
     * Full-text search across title, author and description using a JPQL
     * {@code LIKE} query.
     *
     * <p>The {@code :keyword} parameter is wrapped in wildcards so that
     * matches anywhere in the field are returned.</p>
     *
     * @param keyword the search term
     * @return list of matching books
     */
    @Query("""
            SELECT b FROM Book b
            WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<Book> searchByKeyword(@Param("keyword") String keyword);
}
