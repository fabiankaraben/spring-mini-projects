package com.example.reactivemongodbapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Book} domain entity.
 *
 * <p>These tests verify pure domain logic — constructor behaviour, field defaults,
 * and getter/setter contracts. No Spring context, no database, no mocks, no Docker.
 *
 * <p>Why test the domain entity?
 * <ul>
 *   <li>Ensure the convenience constructor maps all parameters to the correct fields.</li>
 *   <li>Catch accidental field re-ordering bugs in the constructor signature early.</li>
 *   <li>Document expected default values (e.g., {@code id} starts as {@code null}).</li>
 *   <li>Verify that MongoDB-specific fields like {@code genres} (a BSON array mapped to
 *       {@link List}) are handled correctly without a live MongoDB instance.</li>
 * </ul>
 *
 * <p>These tests run in milliseconds because they do not start any containers or
 * Spring contexts.
 */
@DisplayName("Book domain unit tests")
class BookDomainTest {

    // ── Constructor tests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("convenience constructor sets all fields correctly")
    void constructor_setsAllFieldsCorrectly() {
        // Given / When: create a book using the convenience constructor
        List<String> genres = List.of("fiction", "dystopia");
        Book book = new Book(
                "Nineteen Eighty-Four",
                "George Orwell",
                "978-0-452-28423-4",
                12.99,
                1949,
                genres,
                "A dystopian novel set in a totalitarian state.",
                "English",
                328,
                true
        );

        // Then: every field should match the constructor argument
        assertThat(book.getTitle()).isEqualTo("Nineteen Eighty-Four");
        assertThat(book.getAuthor()).isEqualTo("George Orwell");
        assertThat(book.getIsbn()).isEqualTo("978-0-452-28423-4");
        assertThat(book.getPrice()).isEqualTo(12.99);
        assertThat(book.getPublishedYear()).isEqualTo(1949);
        assertThat(book.getGenres()).containsExactly("fiction", "dystopia");
        assertThat(book.getDescription()).isEqualTo("A dystopian novel set in a totalitarian state.");
        assertThat(book.getLanguage()).isEqualTo("English");
        assertThat(book.getPageCount()).isEqualTo(328);
        assertThat(book.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("id is null before persistence (MongoDB generates ObjectId on insert)")
    void id_isNullBeforePersistence() {
        // The id field must be null so the Reactive MongoDB driver issues an insert
        // and generates a new ObjectId. A non-null id would trigger an upsert.
        Book book = new Book("Title", "Author", "ISBN", 1.00, 2020,
                List.of(), "Desc", "English", 100, true);
        assertThat(book.getId()).isNull();
    }

    @Test
    @DisplayName("audit timestamps are null before Spring Data populates them")
    void auditTimestamps_areNullBeforePersistence() {
        // @CreatedDate and @LastModifiedDate are set by Spring Data Reactive MongoDB
        // auditing during save(); they should be null before the document is persisted.
        Book book = new Book("Title", "Author", "ISBN", 1.00, 2020,
                List.of(), "Desc", "English", 100, true);
        assertThat(book.getCreatedAt()).isNull();
        assertThat(book.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("no-arg constructor creates an instance with Java primitive defaults")
    void noArgConstructor_createsInstanceWithDefaults() {
        // Required by Spring Data MongoDB for BSON document-to-Java mapping (reflection)
        Book book = new Book();
        assertThat(book.getId()).isNull();
        assertThat(book.getTitle()).isNull();
        // int fields default to 0 (Java primitive default)
        assertThat(book.getPublishedYear()).isZero();
        assertThat(book.getPageCount()).isZero();
        // boolean fields default to false (Java primitive default)
        assertThat(book.isAvailable()).isFalse();
    }

    // ── Setter / getter round-trip tests ──────────────────────────────────────────

    @Test
    @DisplayName("setters update the corresponding fields")
    void setters_updateFields() {
        Book book = new Book();

        // When: set every field via setters
        book.setId("64a7f8e2b3c9d1e4f5a6b7c8");
        book.setTitle("Animal Farm");
        book.setAuthor("George Orwell");
        book.setIsbn("978-0-452-28424-1");
        book.setPrice(9.99);
        book.setPublishedYear(1945);
        book.setGenres(List.of("satire", "political fiction"));
        book.setDescription("A satirical allegorical novella.");
        book.setLanguage("English");
        book.setPageCount(112);
        book.setAvailable(true);

        // Then: getters return the newly set values
        assertThat(book.getId()).isEqualTo("64a7f8e2b3c9d1e4f5a6b7c8");
        assertThat(book.getTitle()).isEqualTo("Animal Farm");
        assertThat(book.getAuthor()).isEqualTo("George Orwell");
        assertThat(book.getIsbn()).isEqualTo("978-0-452-28424-1");
        assertThat(book.getPrice()).isEqualTo(9.99);
        assertThat(book.getPublishedYear()).isEqualTo(1945);
        assertThat(book.getGenres()).containsExactlyInAnyOrder("satire", "political fiction");
        assertThat(book.getDescription()).isEqualTo("A satirical allegorical novella.");
        assertThat(book.getLanguage()).isEqualTo("English");
        assertThat(book.getPageCount()).isEqualTo(112);
        assertThat(book.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("unavailable book has available flag set to false")
    void unavailableBook_hasFalseAvailableFlag() {
        // A book can be created as unavailable (e.g., out-of-print editions)
        Book book = new Book("Out of Print", "Author", "ISBN", 10.00, 1990,
                List.of(), "Desc", "English", 200, false);
        assertThat(book.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("genres list is stored and retrieved correctly")
    void genres_storedAndRetrievedCorrectly() {
        // MongoDB stores genres as a BSON array; verify the Java List round-trips correctly
        List<String> genres = List.of("science fiction", "space opera", "adventure");
        Book book = new Book("Dune", "Frank Herbert", "978-0-441-17271-9",
                14.99, 1965, genres, "Desc", "English", 412, true);

        assertThat(book.getGenres()).hasSize(3);
        assertThat(book.getGenres()).containsExactly("science fiction", "space opera", "adventure");
    }

    @Test
    @DisplayName("null genres list is accepted without throwing")
    void nullGenres_isAccepted() {
        // genres is optional — a book may not have genres tagged
        Book book = new Book("Title", "Author", "ISBN", 1.00, 2020,
                null, "Desc", "English", 100, true);
        assertThat(book.getGenres()).isNull();
    }
}
