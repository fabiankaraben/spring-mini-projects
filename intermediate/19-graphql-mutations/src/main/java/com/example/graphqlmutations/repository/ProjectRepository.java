package com.example.graphqlmutations.repository;

import com.example.graphqlmutations.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Project} entities.
 *
 * <p>Extending {@link JpaRepository} gives us a full suite of CRUD operations
 * (save, findById, findAll, delete, etc.) without writing any implementation code.
 * Spring Data generates the SQL queries at startup by introspecting the method names
 * and the entity's JPA mappings.
 *
 * <p>The {@code @Repository} annotation is technically redundant here because
 * Spring Data repositories are already auto-detected, but it makes the intent
 * explicit in code and enables Spring's exception translation for JPA exceptions.
 *
 * <p>The type parameters are:
 * <ul>
 *   <li>{@code Project} – the entity type managed by this repository.</li>
 *   <li>{@code Long} – the type of the entity's primary key.</li>
 * </ul>
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Find projects whose name contains the given string (case-insensitive).
     *
     * <p>Spring Data derives the query from the method name:
     * {@code findBy} + {@code Name} + {@code ContainingIgnoreCase}.
     * The generated JPQL is equivalent to:
     * <pre>{@code SELECT p FROM Project p WHERE LOWER(p.name) LIKE LOWER('%name%')}</pre>
     *
     * @param name the substring to search for in project names
     * @return list of matching projects (empty list if none found)
     */
    List<Project> findByNameContainingIgnoreCase(String name);
}
