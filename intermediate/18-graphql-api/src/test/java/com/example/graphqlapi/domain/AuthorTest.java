package com.example.graphqlapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Author} domain entity.
 *
 * <p>These tests verify the domain object's construction and accessor behaviour
 * in complete isolation — no Spring context, no database, no network. They run
 * extremely fast (milliseconds) and serve as the innermost layer of the test pyramid.
 *
 * <p>Testing the domain model directly ensures that business invariants encoded
 * in constructors and setters are correct before any other layer depends on them.
 */
@DisplayName("Author domain entity tests")
class AuthorTest {

    @Test
    @DisplayName("Constructor initialises name and bio correctly")
    void constructor_setsNameAndBio() {
        // Arrange & Act
        Author author = new Author("George Orwell", "English novelist and essayist.");

        // Assert – verify every field set by the constructor
        assertThat(author.getName()).isEqualTo("George Orwell");
        assertThat(author.getBio()).isEqualTo("English novelist and essayist.");
    }

    @Test
    @DisplayName("Constructor allows null bio")
    void constructor_allowsNullBio() {
        // A bio is optional; no bio should be accepted without throwing
        Author author = new Author("Unknown Author", null);

        assertThat(author.getName()).isEqualTo("Unknown Author");
        assertThat(author.getBio()).isNull();
    }

    @Test
    @DisplayName("setName updates the name field")
    void setName_updatesName() {
        Author author = new Author("Old Name", null);

        author.setName("New Name");

        assertThat(author.getName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("setBio updates the bio field")
    void setBio_updatesBio() {
        Author author = new Author("Name", null);

        author.setBio("A detailed biography.");

        assertThat(author.getBio()).isEqualTo("A detailed biography.");
    }

    @Test
    @DisplayName("Books list is initialised as an empty list (not null)")
    void books_initiallyEmpty() {
        // Verify that getBooks() never returns null — callers depend on this guarantee
        Author author = new Author("Author", null);

        assertThat(author.getBooks()).isNotNull();
        assertThat(author.getBooks()).isEmpty();
    }

    @Test
    @DisplayName("id is null before persistence")
    void id_isNullBeforePersistence() {
        // A new Author (not yet saved to the DB) must have a null id
        Author author = new Author("Test Author", "Bio");

        assertThat(author.getId()).isNull();
    }
}
