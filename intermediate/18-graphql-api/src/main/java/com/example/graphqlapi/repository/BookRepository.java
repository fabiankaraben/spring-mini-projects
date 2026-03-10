package com.example.graphqlapi.repository;

import com.example.graphqlapi.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Book} entities.
 *
 * <p>Provides standard CRUD operations via {@link JpaRepository} plus custom
 * derived query methods for filtering books by genre and author.
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Find all books that belong to a given genre (case-insensitive).
     *
     * <p>Spring Data translates this to:
     * {@code SELECT b FROM Book b WHERE LOWER(b.genre) = LOWER(:genre)}
     *
     * @param genre the genre to filter by
     * @return list of books in the given genre
     */
    List<Book> findByGenreIgnoreCase(String genre);

    /**
     * Find all books written by a specific author, identified by the author's ID.
     *
     * <p>Spring Data translates this to:
     * {@code SELECT b FROM Book b WHERE b.author.id = :authorId}
     *
     * @param authorId the primary key of the author
     * @return list of books by that author
     */
    List<Book> findByAuthorId(Long authorId);

    /**
     * Find books whose title contains the given string (case-insensitive).
     *
     * @param title the substring to search for within book titles
     * @return list of matching books
     */
    List<Book> findByTitleContainingIgnoreCase(String title);
}
