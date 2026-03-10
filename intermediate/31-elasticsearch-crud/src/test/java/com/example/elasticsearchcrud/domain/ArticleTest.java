package com.example.elasticsearchcrud.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Article} domain entity.
 *
 * <p>These tests verify the domain object's constructor logic, timestamp
 * initialisation, and getter/setter behaviour in complete isolation — no Spring
 * context, no Elasticsearch, no Mockito needed.
 *
 * <p>Domain-layer tests are the fastest tests in the test pyramid: they run
 * purely in-process and complete in milliseconds.
 */
@DisplayName("Article domain entity tests")
class ArticleTest {

    // ── Constructor ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Constructor sets all provided fields correctly")
    void constructor_setsAllFields() {
        // When: create an Article using the convenience constructor
        Article article = new Article(
                "Spring Boot Guide",
                "A comprehensive guide to Spring Boot",
                "Jane Doe",
                "technology",
                0
        );

        // Then: all fields have the values supplied to the constructor
        assertThat(article.getTitle()).isEqualTo("Spring Boot Guide");
        assertThat(article.getContent()).isEqualTo("A comprehensive guide to Spring Boot");
        assertThat(article.getAuthor()).isEqualTo("Jane Doe");
        assertThat(article.getCategory()).isEqualTo("technology");
        assertThat(article.getViewCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Constructor sets createdAt and updatedAt to a non-null Instant")
    void constructor_setsTimestamps() {
        // Capture the time just before construction to verify the timestamps are recent
        Instant before = Instant.now();

        Article article = new Article("Title", "Content", "Author", "cat", 5);

        Instant after = Instant.now();

        // Then: timestamps are set and fall within the test execution window
        assertThat(article.getCreatedAt())
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
        assertThat(article.getUpdatedAt())
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("No-arg constructor leaves id and timestamps null")
    void noArgConstructor_leavesNullFields() {
        // When: use the default no-arg constructor (required by Spring Data / Jackson)
        Article article = new Article();

        // Then: ID and timestamps are null (Spring Data or Elasticsearch will set them)
        assertThat(article.getId()).isNull();
        assertThat(article.getCreatedAt()).isNull();
        assertThat(article.getUpdatedAt()).isNull();
    }

    // ── Setters ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setId changes the document ID")
    void setId_updatesId() {
        Article article = new Article();
        article.setId("abc-123");
        assertThat(article.getId()).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("setTitle changes the title")
    void setTitle_updatesTitle() {
        Article article = new Article("Old Title", "content", "author", "cat", 0);
        article.setTitle("New Title");
        assertThat(article.getTitle()).isEqualTo("New Title");
    }

    @Test
    @DisplayName("setContent changes the content")
    void setContent_updatesContent() {
        Article article = new Article("title", "Old Content", "author", "cat", 0);
        article.setContent("New Content");
        assertThat(article.getContent()).isEqualTo("New Content");
    }

    @Test
    @DisplayName("setAuthor changes the author")
    void setAuthor_updatesAuthor() {
        Article article = new Article("title", "content", "Old Author", "cat", 0);
        article.setAuthor("New Author");
        assertThat(article.getAuthor()).isEqualTo("New Author");
    }

    @Test
    @DisplayName("setCategory changes the category")
    void setCategory_updatesCategory() {
        Article article = new Article("title", "content", "author", "old-cat", 0);
        article.setCategory("new-cat");
        assertThat(article.getCategory()).isEqualTo("new-cat");
    }

    @Test
    @DisplayName("setViewCount changes the view count")
    void setViewCount_updatesViewCount() {
        Article article = new Article("title", "content", "author", "cat", 0);
        article.setViewCount(42);
        assertThat(article.getViewCount()).isEqualTo(42);
    }

    @Test
    @DisplayName("setUpdatedAt changes the updatedAt timestamp")
    void setUpdatedAt_updatesTimestamp() {
        Article article = new Article("title", "content", "author", "cat", 0);
        Instant newTime = Instant.parse("2025-01-01T00:00:00Z");
        article.setUpdatedAt(newTime);
        assertThat(article.getUpdatedAt()).isEqualTo(newTime);
    }
}
