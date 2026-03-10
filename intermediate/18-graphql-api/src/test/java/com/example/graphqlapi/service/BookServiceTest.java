package com.example.graphqlapi.service;

import com.example.graphqlapi.domain.Author;
import com.example.graphqlapi.domain.Book;
import com.example.graphqlapi.dto.BookInput;
import com.example.graphqlapi.repository.AuthorRepository;
import com.example.graphqlapi.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BookService}.
 *
 * <p>All repository dependencies are mocked with Mockito, so these tests run
 * without a database or Spring application context. This makes them fast and
 * deterministic — ideal for CI pipelines and tight feedback loops.
 *
 * <p>Key scenarios covered:
 * <ul>
 *   <li>Read operations delegate correctly to the repository.</li>
 *   <li>Create resolves the author by ID and saves the new book.</li>
 *   <li>Create throws {@link IllegalArgumentException} when the author is missing.</li>
 *   <li>Update applies field changes and handles the "not found" case.</li>
 *   <li>Delete returns the correct boolean and delegates to the repository.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookService unit tests")
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private AuthorRepository authorRepository;

    /**
     * Mockito injects both mocks via the two-argument constructor of {@link BookService}.
     */
    @InjectMocks
    private BookService bookService;

    private Author sampleAuthor;
    private Book sampleBook;

    @BeforeEach
    void setUp() {
        sampleAuthor = new Author("Isaac Asimov", "Science fiction author");
        sampleBook = new Book("Foundation", "978-0-553-29335-7",
                LocalDate.of(1951, 8, 21), "Science Fiction", sampleAuthor);
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns the list from the repository")
    void findAll_returnsBooks() {
        when(bookRepository.findAll()).thenReturn(List.of(sampleBook));

        List<Book> result = bookService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Foundation");
        verify(bookRepository).findAll();
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns Optional with book when found")
    void findById_returnsBook_whenFound() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));

        Optional<Book> result = bookService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getIsbn()).isEqualTo("978-0-553-29335-7");
    }

    @Test
    @DisplayName("findById returns empty Optional when not found")
    void findById_returnsEmpty_whenNotFound() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(bookService.findById(99L)).isEmpty();
    }

    // ── findByGenre ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByGenre delegates to repository with the correct genre")
    void findByGenre_delegatesToRepository() {
        when(bookRepository.findByGenreIgnoreCase("Science Fiction"))
                .thenReturn(List.of(sampleBook));

        List<Book> result = bookService.findByGenre("Science Fiction");

        assertThat(result).hasSize(1);
        verify(bookRepository).findByGenreIgnoreCase("Science Fiction");
    }

    // ── findByAuthorId ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByAuthorId delegates to repository with the correct authorId")
    void findByAuthorId_delegatesToRepository() {
        when(bookRepository.findByAuthorId(1L)).thenReturn(List.of(sampleBook));

        List<Book> result = bookService.findByAuthorId(1L);

        assertThat(result).hasSize(1);
        verify(bookRepository).findByAuthorId(1L);
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create resolves author, builds Book, and calls save")
    void create_savesNewBook() {
        when(authorRepository.findById(1L)).thenReturn(Optional.of(sampleAuthor));
        when(bookRepository.save(any(Book.class))).thenReturn(sampleBook);

        BookInput input = new BookInput("Foundation", "978-0-553-29335-7",
                LocalDate.of(1951, 8, 21), "Science Fiction", 1L);

        Book result = bookService.create(input);

        assertThat(result.getTitle()).isEqualTo("Foundation");
        verify(authorRepository).findById(1L);
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    @DisplayName("create throws IllegalArgumentException when author is not found")
    void create_throwsException_whenAuthorNotFound() {
        when(authorRepository.findById(99L)).thenReturn(Optional.empty());

        BookInput input = new BookInput("Title", "ISBN", null, null, 99L);

        // Verify that the service fails fast with a descriptive message
        assertThatThrownBy(() -> bookService.create(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        // The book should NOT be saved if the author lookup fails
        verify(bookRepository, never()).save(any());
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update returns empty when book is not found")
    void update_returnsEmpty_whenNotFound() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Book> result = bookService.update(99L,
                new BookInput("T", "I", null, null, 1L));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("update modifies the book fields when found")
    void update_modifiesFields_whenFound() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
        when(authorRepository.findById(1L)).thenReturn(Optional.of(sampleAuthor));

        BookInput input = new BookInput("Foundation and Empire", "978-0-553-29337-1",
                LocalDate.of(1952, 6, 1), "Science Fiction", 1L);

        Optional<Book> result = bookService.update(1L, input);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Foundation and Empire");
        assertThat(result.get().getIsbn()).isEqualTo("978-0-553-29337-1");
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById returns false when book does not exist")
    void deleteById_returnsFalse_whenNotFound() {
        when(bookRepository.existsById(99L)).thenReturn(false);

        assertThat(bookService.deleteById(99L)).isFalse();
        verify(bookRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteById returns true and calls repository.deleteById when found")
    void deleteById_returnsTrue_andDeletes_whenFound() {
        when(bookRepository.existsById(1L)).thenReturn(true);

        assertThat(bookService.deleteById(1L)).isTrue();
        verify(bookRepository).deleteById(1L);
    }
}
