package com.example.jpaauditing.repository;

import com.example.jpaauditing.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Article} entities.
 *
 * <p>By extending {@link JpaRepository}, this interface inherits a complete set
 * of CRUD and pagination operations without any implementation code:
 * <ul>
 *   <li>{@code save(entity)}       — INSERT or UPDATE; triggers the JPA Auditing
 *       lifecycle callbacks that populate/refresh audit timestamps.</li>
 *   <li>{@code findById(id)}       — SELECT by primary key.</li>
 *   <li>{@code findAll()}          — SELECT all rows.</li>
 *   <li>{@code deleteById(id)}     — DELETE by primary key.</li>
 * </ul>
 *
 * <p>Custom query methods are declared below using Spring Data's
 * method-name derivation convention, which translates method names into SQL
 * automatically — no JPQL or {@code @Query} annotation needed.
 */
@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    /**
     * Finds all articles written by a specific author.
     *
     * <p>Spring Data translates this method name into:
     * {@code SELECT * FROM articles WHERE author = :author}
     *
     * @param author the author's name to search for
     * @return a (possibly empty) list of articles by that author
     */
    List<Article> findByAuthor(String author);

    /**
     * Finds an article by its unique title.
     *
     * <p>Spring Data translates this into:
     * {@code SELECT * FROM articles WHERE title = :title LIMIT 1}
     *
     * @param title the exact title to look up
     * @return an {@link Optional} containing the article, or empty if not found
     */
    Optional<Article> findByTitle(String title);
}
