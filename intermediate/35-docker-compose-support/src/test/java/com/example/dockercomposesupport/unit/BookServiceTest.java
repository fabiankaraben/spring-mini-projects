package com.example.dockercomposesupport.unit;

import com.example.dockercomposesupport.domain.Book;
import com.example.dockercomposesupport.dto.BookRequest;
import com.example.dockercomposesupport.dto.BookResponse;
import com.example.dockercomposesupport.exception.BookNotFoundException;
import com.example.dockercomposesupport.exception.DuplicateBookException;
import com.example.dockercomposesupport.repository.BookRepository;
import com.example.dockercomposesupport.service.BookService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BookService}.
 *
 * <h2>Testing strategy</h2>
 * <p>These are pure unit tests — no Spring context is started, no database
 * is involved. The {@link BookRepository} dependency is replaced with a
 * Mockito mock, allowing the service's business logic to be tested in
 * complete isolation.</p>
 *
 * <h2>Key annotations</h2>
 * <ul>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} — activates Mockito's JUnit 5
 *       extension, which processes {@code @Mock} and {@code @InjectMocks} fields
 *       automatically before each test.</li>
 *   <li>{@code @Mock} — creates a Mockito mock for {@link BookRepository}.</li>
 *   <li>{@code @InjectMocks} — creates an instance of {@link BookService} and
 *       injects all {@code @Mock} fields into its constructor.</li>
 * </ul>
 *
 * <h2>AssertJ vs JUnit assertions</h2>
 * <p>We use AssertJ ({@code assertThat}) instead of JUnit's {@code assertEquals}
 * because AssertJ produces richer failure messages and supports fluent chaining.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookService Unit Tests")
class BookServiceTest {

    /** Mock repository — no real DB interaction. */
    @Mock
    private BookRepository bookRepository;

    /** The system under test — receives the mock repository via constructor injection. */
    @InjectMocks
    private BookService bookService;

    // =========================================================================
    // Helper: build a Book entity with a fake auto-generated ID
    // =========================================================================

    /**
     * Creates a {@link Book} entity that simulates what the database would return
     * after a successful save (i.e. with an ID assigned).
     *
     * <p>We use reflection via a Book subclass trick — instead, we simply use the
     * public constructor and then rely on Mockito's {@code thenReturn} to return it
     * as the result of {@code bookRepository.save(...)}.</p>
     */
    private Book buildPersistedBook(Long id, String title, String author,
                                     String isbn, int year, String desc) {
        Book book = new Book(title, author, isbn, year, desc);
        // Trigger the lifecycle callback manually (since JPA isn't running here)
        // We do this by calling the method via reflection is complex; instead
        // we rely on the fact that createdAt/updatedAt are set by @PrePersist which
        // only fires during JPA save — in unit tests we only verify non-timestamp fields.
        return book;
    }

    // =========================================================================
    // createBook
    // =========================================================================

    @Test
    @DisplayName("createBook — should save and return book when title and ISBN are unique")
    void createBook_succeeds_whenNoConflicts() {
        // Arrange
        BookRequest request = new BookRequest(
                "Clean Code", "Robert Martin", "978-0132350884", 2008, "A handbook of agile craftsmanship");

        // Mock: no duplicates exist
        when(bookRepository.existsByTitle(request.title())).thenReturn(false);
        when(bookRepository.existsByIsbn(request.isbn())).thenReturn(false);

        // Mock: save() returns the entity (simulating DB persistence)
        Book savedBook = buildPersistedBook(1L, request.title(), request.author(),
                request.isbn(), request.publicationYear(), request.description());
        when(bookRepository.save(any(Book.class))).thenReturn(savedBook);

        // Act
        BookResponse response = bookService.createBook(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Clean Code");
        assertThat(response.author()).isEqualTo("Robert Martin");
        assertThat(response.isbn()).isEqualTo("978-0132350884");
        assertThat(response.publicationYear()).isEqualTo(2008);

        // Verify that the repository was called exactly once with any Book argument
        verify(bookRepository, times(1)).save(any(Book.class));
    }

    @Test
    @DisplayName("createBook — should throw DuplicateBookException when title exists")
    void createBook_throwsDuplicate_whenTitleExists() {
        // Arrange
        BookRequest request = new BookRequest(
                "Clean Code", "Robert Martin", "978-0132350884", 2008, null);

        // Mock: title already taken
        when(bookRepository.existsByTitle(request.title())).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> bookService.createBook(request))
                .isInstanceOf(DuplicateBookException.class)
                .hasMessageContaining("Clean Code");

        // Verify that save() was NEVER called (early exit on duplicate)
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBook — should throw DuplicateBookException when ISBN exists")
    void createBook_throwsDuplicate_whenIsbnExists() {
        // Arrange
        BookRequest request = new BookRequest(
                "New Title", "Some Author", "978-0132350884", 2020, null);

        // Mock: title is free but ISBN is taken
        when(bookRepository.existsByTitle(request.title())).thenReturn(false);
        when(bookRepository.existsByIsbn(request.isbn())).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> bookService.createBook(request))
                .isInstanceOf(DuplicateBookException.class)
                .hasMessageContaining("978-0132350884");

        verify(bookRepository, never()).save(any());
    }

    // =========================================================================
    // getAllBooks
    // =========================================================================

    @Test
    @DisplayName("getAllBooks — should return all books from repository")
    void getAllBooks_returnsAllBooks() {
        // Arrange — two books in DB
        Book book1 = new Book("Book One", "Author A", "ISBN-001", 2001, "Desc 1");
        Book book2 = new Book("Book Two", "Author B", "ISBN-002", 2002, "Desc 2");
        when(bookRepository.findAll()).thenReturn(List.of(book1, book2));

        // Act
        List<BookResponse> result = bookService.getAllBooks();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(BookResponse::title)
                .containsExactlyInAnyOrder("Book One", "Book Two");
    }

    @Test
    @DisplayName("getAllBooks — should return empty list when no books exist")
    void getAllBooks_returnsEmptyList_whenNoBooksExist() {
        // Arrange
        when(bookRepository.findAll()).thenReturn(List.of());

        // Act
        List<BookResponse> result = bookService.getAllBooks();

        // Assert
        assertThat(result).isEmpty();
    }

    // =========================================================================
    // getBookById
    // =========================================================================

    @Test
    @DisplayName("getBookById — should return book when found")
    void getBookById_returnsBook_whenFound() {
        // Arrange
        Book book = new Book("Effective Java", "Joshua Bloch", "978-0134685991", 2018, "Java best practices");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        // Act
        BookResponse response = bookService.getBookById(1L);

        // Assert
        assertThat(response.title()).isEqualTo("Effective Java");
        assertThat(response.author()).isEqualTo("Joshua Bloch");
    }

    @Test
    @DisplayName("getBookById — should throw BookNotFoundException when not found")
    void getBookById_throwsNotFound_whenBookMissing() {
        // Arrange
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> bookService.getBookById(99L))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining("99");
    }

    // =========================================================================
    // findByAuthor
    // =========================================================================

    @Test
    @DisplayName("findByAuthor — should return books matching author")
    void findByAuthor_returnsMatchingBooks() {
        // Arrange
        Book book = new Book("Design Patterns", "Gang of Four", "ISBN-GOF", 1994, "Classic patterns");
        when(bookRepository.findByAuthorContainingIgnoreCase("Gang")).thenReturn(List.of(book));

        // Act
        List<BookResponse> result = bookService.findByAuthor("Gang");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).author()).isEqualTo("Gang of Four");
    }

    // =========================================================================
    // search
    // =========================================================================

    @Test
    @DisplayName("search — should return books matching keyword")
    void search_returnsMatchingBooks() {
        // Arrange
        Book book = new Book("The Pragmatic Programmer", "Andrew Hunt", "ISBN-PP", 1999, "Pragmatic tips");
        when(bookRepository.searchByKeyword("pragmatic")).thenReturn(List.of(book));

        // Act
        List<BookResponse> result = bookService.search("pragmatic");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).contains("Pragmatic");
    }

    // =========================================================================
    // updateBook
    // =========================================================================

    @Test
    @DisplayName("updateBook — should update fields when book exists and no conflicts")
    void updateBook_succeeds_whenNoConflicts() {
        // Arrange
        Book existing = new Book("Old Title", "Old Author", "OLD-ISBN", 2000, "Old desc");
        BookRequest updateRequest = new BookRequest(
                "New Title", "New Author", "NEW-ISBN", 2024, "New desc");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookRepository.existsByTitle("New Title")).thenReturn(false);
        when(bookRepository.existsByIsbn("NEW-ISBN")).thenReturn(false);

        Book updatedBook = new Book("New Title", "New Author", "NEW-ISBN", 2024, "New desc");
        when(bookRepository.save(any(Book.class))).thenReturn(updatedBook);

        // Act
        BookResponse response = bookService.updateBook(1L, updateRequest);

        // Assert
        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.author()).isEqualTo("New Author");
        assertThat(response.isbn()).isEqualTo("NEW-ISBN");
        assertThat(response.publicationYear()).isEqualTo(2024);

        verify(bookRepository, times(1)).save(any(Book.class));
    }

    @Test
    @DisplayName("updateBook — should throw BookNotFoundException when book does not exist")
    void updateBook_throwsNotFound_whenBookMissing() {
        // Arrange
        BookRequest request = new BookRequest("Title", "Author", "ISBN", 2024, null);
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> bookService.updateBook(99L, request))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("updateBook — should not check title uniqueness when title is unchanged")
    void updateBook_skipsUniquenessCheck_whenTitleUnchanged() {
        // Arrange — same title, different other fields
        Book existing = new Book("Same Title", "Old Author", "OLD-ISBN", 2000, null);
        BookRequest request = new BookRequest("Same Title", "New Author", "NEW-ISBN", 2024, null);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        // Title unchanged — no existsByTitle call expected
        when(bookRepository.existsByIsbn("NEW-ISBN")).thenReturn(false);

        Book updatedBook = new Book("Same Title", "New Author", "NEW-ISBN", 2024, null);
        when(bookRepository.save(any(Book.class))).thenReturn(updatedBook);

        // Act
        bookService.updateBook(1L, request);

        // Assert: existsByTitle was NEVER called because the title didn't change
        verify(bookRepository, never()).existsByTitle(any());
    }

    // =========================================================================
    // deleteBook
    // =========================================================================

    @Test
    @DisplayName("deleteBook — should delete book when it exists")
    void deleteBook_succeeds_whenBookExists() {
        // Arrange
        when(bookRepository.existsById(1L)).thenReturn(true);

        // Act — should not throw
        assertThatCode(() -> bookService.deleteBook(1L)).doesNotThrowAnyException();

        // Assert: deleteById was called once
        verify(bookRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("deleteBook — should throw BookNotFoundException when book does not exist")
    void deleteBook_throwsNotFound_whenBookMissing() {
        // Arrange
        when(bookRepository.existsById(99L)).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> bookService.deleteBook(99L))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining("99");

        // Verify deleteById was never called
        verify(bookRepository, never()).deleteById(any());
    }
}
