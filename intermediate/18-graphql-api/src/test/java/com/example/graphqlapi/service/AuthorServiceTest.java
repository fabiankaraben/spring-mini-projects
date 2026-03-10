package com.example.graphqlapi.service;

import com.example.graphqlapi.domain.Author;
import com.example.graphqlapi.dto.AuthorInput;
import com.example.graphqlapi.repository.AuthorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthorService}.
 *
 * <p>These tests exercise the service's business logic in complete isolation
 * from the database. The repository is replaced by a Mockito mock so:
 * <ul>
 *   <li>No PostgreSQL connection or Spring context is needed.</li>
 *   <li>Tests run in milliseconds.</li>
 *   <li>Each test controls exactly what the repository returns, making
 *       assertions deterministic and free of data coupling.</li>
 * </ul>
 *
 * <p>{@link ExtendWith(MockitoExtension.class)} activates Mockito's JUnit 5
 * extension which processes {@code @Mock} and {@code @InjectMocks} annotations
 * automatically before each test method.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorService unit tests")
class AuthorServiceTest {

    /**
     * Mockito mock for the repository. All calls to this object return
     * the values we configure with {@code when(...).thenReturn(...)}.
     * No real database interaction occurs.
     */
    @Mock
    private AuthorRepository authorRepository;

    /**
     * The class under test. Mockito injects the mock repository via
     * constructor injection (matches the single-constructor signature).
     */
    @InjectMocks
    private AuthorService authorService;

    private Author sampleAuthor;

    @BeforeEach
    void setUp() {
        // Build a representative Author instance reused across tests
        sampleAuthor = new Author("George Orwell", "English novelist");
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns the list from the repository")
    void findAll_returnsAuthors() {
        // Arrange: configure the mock to return a list when findAll() is called
        when(authorRepository.findAll()).thenReturn(List.of(sampleAuthor));

        // Act
        List<Author> result = authorService.findAll();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("George Orwell");
        verify(authorRepository).findAll(); // confirm the repository was called
    }

    @Test
    @DisplayName("findAll returns empty list when no authors exist")
    void findAll_returnsEmptyList() {
        when(authorRepository.findAll()).thenReturn(List.of());

        List<Author> result = authorService.findAll();

        assertThat(result).isEmpty();
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns Optional with author when found")
    void findById_returnsAuthor_whenFound() {
        when(authorRepository.findById(1L)).thenReturn(Optional.of(sampleAuthor));

        Optional<Author> result = authorService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("George Orwell");
    }

    @Test
    @DisplayName("findById returns empty Optional when not found")
    void findById_returnsEmpty_whenNotFound() {
        when(authorRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Author> result = authorService.findById(99L);

        assertThat(result).isEmpty();
    }

    // ── searchByName ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("searchByName delegates to repository with the correct argument")
    void searchByName_delegatesToRepository() {
        when(authorRepository.findByNameContainingIgnoreCase("Orwell"))
                .thenReturn(List.of(sampleAuthor));

        List<Author> result = authorService.searchByName("Orwell");

        assertThat(result).hasSize(1);
        verify(authorRepository).findByNameContainingIgnoreCase("Orwell");
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create maps AuthorInput to Author and calls save")
    void create_savesNewAuthor() {
        // Arrange: configure save() to return the same author it receives
        when(authorRepository.save(any(Author.class))).thenReturn(sampleAuthor);

        AuthorInput input = new AuthorInput("George Orwell", "English novelist");

        // Act
        Author result = authorService.create(input);

        // Assert
        assertThat(result.getName()).isEqualTo("George Orwell");
        verify(authorRepository).save(any(Author.class));
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update returns empty when author is not found")
    void update_returnsEmpty_whenNotFound() {
        when(authorRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Author> result = authorService.update(99L,
                new AuthorInput("Name", "Bio"));

        assertThat(result).isEmpty();
        // save() should NOT be called if the author doesn't exist
        verify(authorRepository, never()).save(any());
    }

    @Test
    @DisplayName("update modifies the existing author's fields")
    void update_modifiesFields_whenFound() {
        when(authorRepository.findById(1L)).thenReturn(Optional.of(sampleAuthor));

        AuthorInput input = new AuthorInput("Updated Name", "Updated Bio");
        Optional<Author> result = authorService.update(1L, input);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Updated Name");
        assertThat(result.get().getBio()).isEqualTo("Updated Bio");
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById returns false when author does not exist")
    void deleteById_returnsFalse_whenNotFound() {
        when(authorRepository.existsById(99L)).thenReturn(false);

        boolean result = authorService.deleteById(99L);

        assertThat(result).isFalse();
        // deleteById on the repository should NOT be called
        verify(authorRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteById returns true and calls repository.deleteById when found")
    void deleteById_returnsTrue_andDeletes_whenFound() {
        when(authorRepository.existsById(1L)).thenReturn(true);

        boolean result = authorService.deleteById(1L);

        assertThat(result).isTrue();
        verify(authorRepository).deleteById(1L);
    }
}
