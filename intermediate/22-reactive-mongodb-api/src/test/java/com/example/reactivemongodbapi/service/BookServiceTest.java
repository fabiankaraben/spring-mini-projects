package com.example.reactivemongodbapi.service;

import com.example.reactivemongodbapi.domain.Book;
import com.example.reactivemongodbapi.dto.BookRequest;
import com.example.reactivemongodbapi.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BookService}.
 *
 * <p>These tests verify the service's business logic in pure isolation:
 * <ul>
 *   <li>The {@link BookRepository} is replaced with a Mockito mock, so no real
 *       MongoDB connection or Docker container is needed. Tests run in milliseconds.</li>
 *   <li>No Spring context is loaded — {@link ExtendWith}({@link MockitoExtension}.class)
 *       initialises Mockito annotations only, keeping startup time near zero.</li>
 *   <li>{@link StepVerifier} (from {@code reactor-test}) is used to test reactive
 *       pipelines step-by-step in a synchronous, deterministic way. It subscribes
 *       to a {@link Mono} or {@link Flux}, then asserts each emitted item,
 *       completion, or error signal.</li>
 *   <li>Each test follows the Given / When / Then (Arrange / Act / Assert) pattern.</li>
 * </ul>
 *
 * <p><strong>Why StepVerifier instead of .block()?</strong><br>
 * Reactive types are lazy — nothing executes until subscribed. Calling
 * {@code bookService.findAll()} returns a cold {@link Flux} but does NOT trigger
 * any MongoDB queries. {@code StepVerifier.create(...).expectNext(...).verifyComplete()}
 * subscribes and blocks the test thread until the publisher completes, making
 * reactive assertions feel like ordinary JUnit assertions without bypassing the
 * reactive pipeline.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookService unit tests")
class BookServiceTest {

    /**
     * Mockito mock of the repository — no real MongoDB involved.
     * All interactions are simulated via {@code when(...).thenReturn(...)}.
     */
    @Mock
    private BookRepository bookRepository;

    /**
     * The class under test.
     * {@code @InjectMocks} creates a {@link BookService} instance and injects
     * the {@code @Mock} fields into it via constructor injection.
     */
    @InjectMocks
    private BookService bookService;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    /** A pre-built domain object returned by the mock repository. */
    private Book sampleBook;

    /** A DTO representing an incoming HTTP POST/PUT request body. */
    private BookRequest sampleRequest;

    @BeforeEach
    void setUp() {
        // Build a sample domain entity that the mock repository will return
        sampleBook = new Book(
                "Nineteen Eighty-Four",
                "George Orwell",
                "978-0-452-28423-4",
                12.99,
                1949,
                List.of("fiction", "dystopia"),
                "A dystopian novel set in a totalitarian state.",
                "English",
                328,
                true
        );
        sampleBook.setId("64a7f8e2b3c9d1e4f5a6b7c8");

        // Build the corresponding request DTO
        sampleRequest = new BookRequest(
                "Nineteen Eighty-Four",
                "George Orwell",
                "978-0-452-28423-4",
                12.99,
                1949,
                List.of("fiction", "dystopia"),
                "A dystopian novel set in a totalitarian state.",
                "English",
                328,
                true
        );
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll emits all books from the repository")
    void findAll_emitsAllBooks() {
        // Given: the mock repository returns two books as a Flux
        Book second = new Book("Animal Farm", "George Orwell", "978-0-452-28424-1",
                9.99, 1945, List.of("satire"), "A satirical novella.",
                "English", 112, true);
        second.setId("64a7f8e2b3c9d1e4f5a6b7c9");
        when(bookRepository.findAll()).thenReturn(Flux.just(sampleBook, second));

        // When / Then: StepVerifier subscribes and asserts both items are emitted in order
        StepVerifier.create(bookService.findAll())
                .expectNext(sampleBook)
                .expectNext(second)
                .verifyComplete();

        verify(bookRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll completes empty when the collection is empty")
    void findAll_completesEmpty_whenNoBooks() {
        // Given: the repository returns an empty Flux
        when(bookRepository.findAll()).thenReturn(Flux.empty());

        // When / Then: the Flux completes immediately with no items
        StepVerifier.create(bookService.findAll())
                .verifyComplete();
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById emits the book when it exists")
    void findById_emitsBook_whenFound() {
        // Given: the repository finds the book with the given ObjectId
        when(bookRepository.findById("64a7f8e2b3c9d1e4f5a6b7c8"))
                .thenReturn(Mono.just(sampleBook));

        // When / Then
        StepVerifier.create(bookService.findById("64a7f8e2b3c9d1e4f5a6b7c8"))
                .expectNextMatches(b -> b.getId().equals("64a7f8e2b3c9d1e4f5a6b7c8")
                        && b.getTitle().equals("Nineteen Eighty-Four"))
                .verifyComplete();

        verify(bookRepository, times(1)).findById("64a7f8e2b3c9d1e4f5a6b7c8");
    }

    @Test
    @DisplayName("findById completes empty when the book does not exist")
    void findById_completesEmpty_whenNotFound() {
        // Given: no book with the given id
        when(bookRepository.findById("nonexistentid")).thenReturn(Mono.empty());

        // When / Then: the Mono is empty (the controller will map this to HTTP 404)
        StepVerifier.create(bookService.findById("nonexistentid"))
                .verifyComplete();
    }

    // ── findByIsbn ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByIsbn emits the book when ISBN exists")
    void findByIsbn_emitsBook_whenFound() {
        // Given: the repository finds the book by its unique ISBN
        when(bookRepository.findByIsbn("978-0-452-28423-4"))
                .thenReturn(Mono.just(sampleBook));

        // When / Then
        StepVerifier.create(bookService.findByIsbn("978-0-452-28423-4"))
                .expectNextMatches(b -> "978-0-452-28423-4".equals(b.getIsbn()))
                .verifyComplete();

        verify(bookRepository, times(1)).findByIsbn("978-0-452-28423-4");
    }

    // ── findByAuthor ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByAuthor emits books by the given author")
    void findByAuthor_emitsMatchingBooks() {
        // Given: the repository returns our sample book for "George Orwell"
        when(bookRepository.findByAuthor("George Orwell"))
                .thenReturn(Flux.just(sampleBook));

        // When / Then
        StepVerifier.create(bookService.findByAuthor("George Orwell"))
                .expectNextMatches(b -> "George Orwell".equals(b.getAuthor()))
                .verifyComplete();

        verify(bookRepository, times(1)).findByAuthor("George Orwell");
    }

    @Test
    @DisplayName("findByAuthor completes empty when no books match")
    void findByAuthor_completesEmpty_whenNoMatch() {
        // Given: no books by "Unknown Author"
        when(bookRepository.findByAuthor("Unknown Author")).thenReturn(Flux.empty());

        StepVerifier.create(bookService.findByAuthor("Unknown Author"))
                .verifyComplete();
    }

    // ── findAvailable ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAvailable emits only available books")
    void findAvailable_emitsAvailableBooks() {
        // Given: our sample book is available
        when(bookRepository.findByAvailable(true)).thenReturn(Flux.just(sampleBook));

        // When / Then
        StepVerifier.create(bookService.findAvailable())
                .expectNextMatches(Book::isAvailable)
                .verifyComplete();

        verify(bookRepository, times(1)).findByAvailable(true);
    }

    // ── findByPriceRange ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByPriceRange emits books within the given price range")
    void findByPriceRange_emitsBooks() {
        // Given: sample book price is 12.99, which falls within [10, 20]
        when(bookRepository.findByPriceBetween(10.0, 20.0))
                .thenReturn(Flux.just(sampleBook));

        // When / Then
        StepVerifier.create(bookService.findByPriceRange(10.0, 20.0))
                .expectNextMatches(b -> b.getPrice() >= 10.0 && b.getPrice() <= 20.0)
                .verifyComplete();

        verify(bookRepository, times(1))
                .findByPriceBetween(10.0, 20.0);
    }

    // ── searchByTitle ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("searchByTitle delegates the keyword to the repository for regex matching")
    void searchByTitle_delegatesKeywordToRepository() {
        // Given: searching for "eighty" (case-insensitive) should return our book
        when(bookRepository.findByTitleContaining("eighty"))
                .thenReturn(Flux.just(sampleBook));

        // When / Then
        StepVerifier.create(bookService.searchByTitle("eighty"))
                .expectNextMatches(b -> b.getTitle().toLowerCase().contains("eighty"))
                .verifyComplete();

        // Verify the service passes the keyword as-is (no % wrapping — MongoDB uses regex)
        verify(bookRepository, times(1)).findByTitleContaining("eighty");
    }

    // ── findByGenre ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByGenre emits books containing the given genre in their array")
    void findByGenre_emitsBooksWithGenre() {
        // Given: our sample book includes "fiction" in its genres array
        when(bookRepository.findByGenres("fiction")).thenReturn(Flux.just(sampleBook));

        // When / Then: MongoDB array query — no join needed
        StepVerifier.create(bookService.findByGenre("fiction"))
                .expectNextMatches(b -> b.getGenres().contains("fiction"))
                .verifyComplete();

        verify(bookRepository, times(1)).findByGenres("fiction");
    }

    // ── countByAuthor ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("countByAuthor emits the count of books by the given author")
    void countByAuthor_emitsCount() {
        // Given: 2 books by George Orwell
        when(bookRepository.countByAuthor("George Orwell")).thenReturn(Mono.just(2L));

        // When / Then
        StepVerifier.create(bookService.countByAuthor("George Orwell"))
                .expectNext(2L)
                .verifyComplete();

        verify(bookRepository, times(1)).countByAuthor("George Orwell");
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create persists the book and emits the saved document with generated id")
    void create_persistsBookAndEmitsSaved() {
        // Given: no duplicate ISBN exists
        when(bookRepository.existsByIsbn("978-0-452-28423-4")).thenReturn(Mono.just(false));
        // The mock repository simulates MongoDB assigning an ObjectId
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book b = invocation.getArgument(0);
            b.setId("64a7f8e2b3c9d1e4f5a6b7c8"); // simulate MongoDB-generated ObjectId
            return Mono.just(b);
        });

        // When / Then: the Mono emits the saved book with a generated id
        StepVerifier.create(bookService.create(sampleRequest))
                .expectNextMatches(saved ->
                        "64a7f8e2b3c9d1e4f5a6b7c8".equals(saved.getId())
                        && "Nineteen Eighty-Four".equals(saved.getTitle())
                        && "George Orwell".equals(saved.getAuthor())
                        && saved.getPrice() == 12.99
                        && saved.isAvailable())
                .verifyComplete();

        verify(bookRepository, times(1)).existsByIsbn("978-0-452-28423-4");
        verify(bookRepository, times(1)).save(any(Book.class));
    }

    @Test
    @DisplayName("create emits error when a book with the same ISBN already exists")
    void create_emitsError_whenDuplicateIsbn() {
        // Given: a book with this ISBN already exists in MongoDB
        when(bookRepository.existsByIsbn("978-0-452-28423-4")).thenReturn(Mono.just(true));

        // When / Then: the service emits an IllegalStateException (mapped to HTTP 409)
        StepVerifier.create(bookService.create(sampleRequest))
                .expectErrorMatches(ex ->
                        ex instanceof IllegalStateException
                        && ex.getMessage().contains("978-0-452-28423-4"))
                .verify();

        // save() must NEVER be called when the ISBN is a duplicate
        verify(bookRepository, never()).save(any(Book.class));
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update emits the updated book when it exists")
    void update_emitsUpdatedBook_whenFound() {
        // Given: the book is found
        when(bookRepository.findById("64a7f8e2b3c9d1e4f5a6b7c8"))
                .thenReturn(Mono.just(sampleBook));
        when(bookRepository.save(any(Book.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // New values to apply
        BookRequest updateRequest = new BookRequest(
                "Nineteen Eighty-Four — Revised Edition",
                "George Orwell",
                "978-0-452-28423-4",
                14.99,
                1949,
                List.of("fiction", "dystopia", "classic"),
                "Updated description.",
                "English",
                340,
                false
        );

        // When / Then
        StepVerifier.create(bookService.update("64a7f8e2b3c9d1e4f5a6b7c8", updateRequest))
                .expectNextMatches(updated ->
                        "Nineteen Eighty-Four — Revised Edition".equals(updated.getTitle())
                        && updated.getPrice() == 14.99
                        && updated.getPageCount() == 340
                        && !updated.isAvailable())
                .verifyComplete();

        verify(bookRepository, times(1)).findById("64a7f8e2b3c9d1e4f5a6b7c8");
        verify(bookRepository, times(1)).save(any(Book.class));
    }

    @Test
    @DisplayName("update completes empty when the book does not exist")
    void update_completesEmpty_whenNotFound() {
        // Given: no book with the given id
        when(bookRepository.findById("nonexistentid")).thenReturn(Mono.empty());

        // When / Then: the service returns an empty Mono (controller maps to 404)
        StepVerifier.create(bookService.update("nonexistentid", sampleRequest))
                .verifyComplete();

        verify(bookRepository, times(1)).findById("nonexistentid");
        // save() must NEVER be called when the book was not found
        verify(bookRepository, never()).save(any(Book.class));
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById emits true when the book exists and is deleted")
    void deleteById_emitsTrue_whenBookExists() {
        // Given: the book is found and deleteById returns Mono<Void>
        when(bookRepository.findById("64a7f8e2b3c9d1e4f5a6b7c8"))
                .thenReturn(Mono.just(sampleBook));
        when(bookRepository.deleteById("64a7f8e2b3c9d1e4f5a6b7c8"))
                .thenReturn(Mono.empty());

        // When / Then: the Mono emits true (book was deleted)
        StepVerifier.create(bookService.deleteById("64a7f8e2b3c9d1e4f5a6b7c8"))
                .expectNext(true)
                .verifyComplete();

        verify(bookRepository, times(1)).findById("64a7f8e2b3c9d1e4f5a6b7c8");
        verify(bookRepository, times(1)).deleteById("64a7f8e2b3c9d1e4f5a6b7c8");
    }

    @Test
    @DisplayName("deleteById emits false when the book does not exist")
    void deleteById_emitsFalse_whenNotFound() {
        // Given: no book with the given id
        when(bookRepository.findById("nonexistentid")).thenReturn(Mono.empty());

        // When / Then: the Mono emits false (nothing was deleted)
        StepVerifier.create(bookService.deleteById("nonexistentid"))
                .expectNext(false)
                .verifyComplete();

        verify(bookRepository, times(1)).findById("nonexistentid");
        // deleteById must NEVER be called when the book was not found
        verify(bookRepository, never()).deleteById(anyString());
    }
}
