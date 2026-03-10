package com.example.reactivewebfluxapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Article} domain entity.
 *
 * <p>These tests verify that the entity's constructor, getters, and setters behave
 * correctly as pure Java logic — no Spring context, no MongoDB, no mocks.
 * Running this class exercises the domain model in complete isolation, which makes
 * tests extremely fast (sub-millisecond) and reliable.
 *
 * <p>Why test the domain class?
 * <ul>
 *   <li>The all-args constructor sets timestamps ({@code createdAt}, {@code updatedAt}).
 *       Testing that verifies the initialization contract.</li>
 *   <li>Setter mutations are tested to ensure the entity is correctly mutable for
 *       update operations.</li>
 *   <li>These tests serve as executable documentation of the entity's invariants.</li>
 * </ul>
 */
@DisplayName("Article domain entity unit tests")
class ArticleTest {

    // ── Constructor ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("All-args constructor sets all fields correctly")
    void constructor_setsAllFields() {
        // When: create an Article using the convenience constructor
        Article article = new Article(
                "Spring WebFlux Guide",
                "A comprehensive guide to reactive programming",
                "Jane Doe",
                "technology",
                true
        );

        // Then: all fields are populated with the provided values
        assertThat(article.getTitle()).isEqualTo("Spring WebFlux Guide");
        assertThat(article.getContent()).isEqualTo("A comprehensive guide to reactive programming");
        assertThat(article.getAuthor()).isEqualTo("Jane Doe");
        assertThat(article.getCategory()).isEqualTo("technology");
        assertThat(article.isPublished()).isTrue();
    }

    @Test
    @DisplayName("Constructor initialises createdAt and updatedAt to a non-null instant")
    void constructor_initialisesTimestamps() {
        // Given: a reference instant captured before construction
        Instant before = Instant.now();

        // When: create the article
        Article article = new Article("Title", "Content", "Author", "category", false);

        // Given: a reference instant captured after construction
        Instant after = Instant.now();

        // Then: both timestamps are set and fall within the expected range
        assertThat(article.getCreatedAt()).isNotNull();
        assertThat(article.getUpdatedAt()).isNotNull();
        // Timestamps should be between the before/after instants (inclusive)
        assertThat(article.getCreatedAt()).isBetween(before, after);
        assertThat(article.getUpdatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("No-arg constructor leaves all fields null/false (default values)")
    void noArgConstructor_leavesFieldsAtDefaults() {
        // When: create an Article using the no-arg constructor (used by MongoDB deserialisation)
        Article article = new Article();

        // Then: all object fields are null, boolean is false by default
        assertThat(article.getId()).isNull();
        assertThat(article.getTitle()).isNull();
        assertThat(article.getContent()).isNull();
        assertThat(article.getAuthor()).isNull();
        assertThat(article.getCategory()).isNull();
        assertThat(article.isPublished()).isFalse();
        assertThat(article.getCreatedAt()).isNull();
        assertThat(article.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Constructor leaves id null so MongoDB generates an ObjectId on insert")
    void constructor_leavesIdNull() {
        // When
        Article article = new Article("Title", "Body", "Author", "cat", false);

        // Then: id must be null — Spring Data MongoDB will assign an ObjectId on save()
        assertThat(article.getId()).isNull();
    }

    // ── Setters ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setId assigns a value that is readable via getId")
    void setId_assignsId() {
        Article article = new Article();
        article.setId("507f1f77bcf86cd799439011");

        assertThat(article.getId()).isEqualTo("507f1f77bcf86cd799439011");
    }

    @Test
    @DisplayName("setTitle updates the title field")
    void setTitle_updatesTitle() {
        Article article = new Article("Old Title", "Content", "Author", "cat", false);
        article.setTitle("New Title");

        assertThat(article.getTitle()).isEqualTo("New Title");
    }

    @Test
    @DisplayName("setContent updates the content field")
    void setContent_updatesContent() {
        Article article = new Article("Title", "Old Content", "Author", "cat", false);
        article.setContent("Updated Content");

        assertThat(article.getContent()).isEqualTo("Updated Content");
    }

    @Test
    @DisplayName("setAuthor updates the author field")
    void setAuthor_updatesAuthor() {
        Article article = new Article("Title", "Content", "Old Author", "cat", false);
        article.setAuthor("New Author");

        assertThat(article.getAuthor()).isEqualTo("New Author");
    }

    @Test
    @DisplayName("setCategory updates the category field")
    void setCategory_updatesCategory() {
        Article article = new Article("Title", "Content", "Author", "old-cat", false);
        article.setCategory("new-cat");

        assertThat(article.getCategory()).isEqualTo("new-cat");
    }

    @Test
    @DisplayName("setPublished toggles the published flag")
    void setPublished_toggelsPublishedFlag() {
        Article article = new Article("Title", "Content", "Author", "cat", false);

        // Toggle to true
        article.setPublished(true);
        assertThat(article.isPublished()).isTrue();

        // Toggle back to false
        article.setPublished(false);
        assertThat(article.isPublished()).isFalse();
    }

    @Test
    @DisplayName("setUpdatedAt updates the updatedAt timestamp")
    void setUpdatedAt_updatesTimestamp() {
        Article article = new Article("Title", "Content", "Author", "cat", false);
        Instant newTime = Instant.parse("2025-06-15T10:00:00Z");

        article.setUpdatedAt(newTime);

        assertThat(article.getUpdatedAt()).isEqualTo(newTime);
    }

    @Test
    @DisplayName("setCreatedAt updates the createdAt timestamp")
    void setCreatedAt_updatesTimestamp() {
        Article article = new Article();
        Instant created = Instant.parse("2025-01-01T00:00:00Z");

        article.setCreatedAt(created);

        assertThat(article.getCreatedAt()).isEqualTo(created);
    }
}
