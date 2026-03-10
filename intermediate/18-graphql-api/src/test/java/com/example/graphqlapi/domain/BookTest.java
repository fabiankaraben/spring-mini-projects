package com.example.graphqlapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Book} domain entity.
 *
 * <p>Validates construction, accessor behaviour, and field mutability
 * in isolation — no Spring context, no database required.
 */
@DisplayName("Book domain entity tests")
class BookTest {

    @Test
    @DisplayName("Constructor initialises all fields correctly")
    void constructor_setsAllFields() {
        // Arrange
        Author author = new Author("J.R.R. Tolkien", "English author");
        LocalDate published = LocalDate.of(1954, 7, 29);

        // Act
        Book book = new Book("The Fellowship of the Ring", "978-0-261-10235-4",
                published, "Fantasy", author);

        // Assert
        assertThat(book.getTitle()).isEqualTo("The Fellowship of the Ring");
        assertThat(book.getIsbn()).isEqualTo("978-0-261-10235-4");
        assertThat(book.getPublishedDate()).isEqualTo(published);
        assertThat(book.getGenre()).isEqualTo("Fantasy");
        assertThat(book.getAuthor()).isSameAs(author);
    }

    @Test
    @DisplayName("Constructor allows null publishedDate and genre")
    void constructor_allowsNullOptionalFields() {
        Author author = new Author("Anonymous", null);

        // publishedDate and genre are optional
        Book book = new Book("Untitled", "000-0-000-00000-0", null, null, author);

        assertThat(book.getPublishedDate()).isNull();
        assertThat(book.getGenre()).isNull();
    }

    @Test
    @DisplayName("id is null before persistence")
    void id_isNullBeforePersistence() {
        Book book = new Book("Title", "ISBN", null, null, null);

        assertThat(book.getId()).isNull();
    }

    @Test
    @DisplayName("setTitle updates the title field")
    void setTitle_updatesTitle() {
        Book book = new Book("Old Title", "ISBN", null, null, null);

        book.setTitle("New Title");

        assertThat(book.getTitle()).isEqualTo("New Title");
    }

    @Test
    @DisplayName("setGenre updates the genre field")
    void setGenre_updatesGenre() {
        Book book = new Book("Title", "ISBN", null, "Old Genre", null);

        book.setGenre("Science Fiction");

        assertThat(book.getGenre()).isEqualTo("Science Fiction");
    }

    @Test
    @DisplayName("setPublishedDate updates the date field")
    void setPublishedDate_updatesDate() {
        Book book = new Book("Title", "ISBN", null, null, null);
        LocalDate newDate = LocalDate.of(2024, 1, 15);

        book.setPublishedDate(newDate);

        assertThat(book.getPublishedDate()).isEqualTo(newDate);
    }

    @Test
    @DisplayName("setAuthor replaces the author reference")
    void setAuthor_replacesAuthor() {
        Author first = new Author("First Author", null);
        Author second = new Author("Second Author", null);
        Book book = new Book("Title", "ISBN", null, null, first);

        book.setAuthor(second);

        assertThat(book.getAuthor()).isSameAs(second);
    }
}
