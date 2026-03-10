package com.example.graphqlapi.repository;

import com.example.graphqlapi.domain.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Author} entities.
 *
 * <p>Extending {@link JpaRepository} gives us a full set of CRUD operations
 * ({@code findById}, {@code findAll}, {@code save}, {@code deleteById}, etc.)
 * without writing any SQL. Spring Data generates the implementation at startup
 * using dynamic proxies.
 *
 * <p>Custom query methods below follow Spring Data's <em>derived query</em>
 * naming convention: Spring parses the method name to produce the appropriate
 * JPQL query automatically.
 */
@Repository
public interface AuthorRepository extends JpaRepository<Author, Long> {

    /**
     * Find authors whose name contains the given string (case-insensitive).
     *
     * <p>Spring Data translates this to:
     * {@code SELECT a FROM Author a WHERE LOWER(a.name) LIKE LOWER('%name%')}
     *
     * @param name the substring to search for within author names
     * @return list of matching authors (empty list if none match)
     */
    List<Author> findByNameContainingIgnoreCase(String name);
}
