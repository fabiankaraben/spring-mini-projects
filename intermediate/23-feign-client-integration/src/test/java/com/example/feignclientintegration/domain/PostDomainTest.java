package com.example.feignclientintegration.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the domain model classes: {@link Post}, {@link Comment}, and {@link User}.
 *
 * <p>These tests verify the domain model's structural integrity and record semantics:
 * <ul>
 *   <li>Field accessors return the values provided to the canonical constructor.</li>
 *   <li>Java record equality: two records with identical field values are equal.</li>
 *   <li>Java record toString: contains field names and values (useful for debugging).</li>
 *   <li>Null handling: records allow null field values where the API may omit fields.</li>
 * </ul>
 *
 * <p>No Spring context or mocks are needed here — these are plain Java unit tests
 * that run in milliseconds and verify the fundamental contracts of our domain layer.
 *
 * <p><strong>Why test records?</strong><br>
 * While Java records auto-generate accessors, {@code equals}, {@code hashCode}, and
 * {@code toString}, testing them explicitly ensures:
 * <ol>
 *   <li>We haven't accidentally overridden any of these methods incorrectly.</li>
 *   <li>The field names and order match what the upstream JSON API sends (important
 *       for Jackson deserialization which relies on field names matching JSON keys).</li>
 *   <li>Future refactoring (e.g., adding validation or custom accessors) won't
 *       silently break consumers of these types.</li>
 * </ol>
 */
@DisplayName("Domain model unit tests")
class PostDomainTest {

    // ── Post record ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Post record accessors return the values provided to the constructor")
    void post_accessorsReturnConstructorValues() {
        // Given / When: create a Post with all fields set
        Post post = new Post(42, 7, "My Title", "My body content");

        // Then: each accessor returns the expected value
        assertThat(post.id()).isEqualTo(42);
        assertThat(post.userId()).isEqualTo(7);
        assertThat(post.title()).isEqualTo("My Title");
        assertThat(post.body()).isEqualTo("My body content");
    }

    @Test
    @DisplayName("Post record equality: two Posts with the same fields are equal")
    void post_recordEquality_twoPostsWithSameFieldsAreEqual() {
        // Given: two Post instances with identical field values
        Post post1 = new Post(1, 1, "Title", "Body");
        Post post2 = new Post(1, 1, "Title", "Body");

        // Then: records use structural equality (not reference equality)
        assertThat(post1).isEqualTo(post2);
        assertThat(post1.hashCode()).isEqualTo(post2.hashCode());
    }

    @Test
    @DisplayName("Post record equality: two Posts with different IDs are not equal")
    void post_recordEquality_postsWithDifferentIdsAreNotEqual() {
        // Given: two Posts that differ only in their id field
        Post post1 = new Post(1, 1, "Same Title", "Same Body");
        Post post2 = new Post(2, 1, "Same Title", "Same Body");

        // Then: they are not equal because id differs
        assertThat(post1).isNotEqualTo(post2);
    }

    @Test
    @DisplayName("Post record allows null id (used for create requests before server assigns id)")
    void post_allowsNullId_forCreateRequests() {
        // Given: a Post with null id (as sent to the upstream API on POST)
        Post post = new Post(null, 1, "New Post", "Content");

        // Then: the null id is preserved
        assertThat(post.id()).isNull();
        assertThat(post.userId()).isEqualTo(1);
        assertThat(post.title()).isEqualTo("New Post");
    }

    @Test
    @DisplayName("Post toString contains all field names and values")
    void post_toStringContainsAllFields() {
        // Given
        Post post = new Post(5, 2, "Test Title", "Test Body");

        // When
        String str = post.toString();

        // Then: Java records include all field names and values in toString
        assertThat(str).contains("id=5");
        assertThat(str).contains("userId=2");
        assertThat(str).contains("title=Test Title");
        assertThat(str).contains("body=Test Body");
    }

    // ── Comment record ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Comment record accessors return the values provided to the constructor")
    void comment_accessorsReturnConstructorValues() {
        // Given / When
        Comment comment = new Comment(10, 3, "Subject Line", "user@test.com", "Comment text");

        // Then
        assertThat(comment.id()).isEqualTo(10);
        assertThat(comment.postId()).isEqualTo(3);
        assertThat(comment.name()).isEqualTo("Subject Line");
        assertThat(comment.email()).isEqualTo("user@test.com");
        assertThat(comment.body()).isEqualTo("Comment text");
    }

    @Test
    @DisplayName("Comment record equality: two Comments with the same fields are equal")
    void comment_recordEquality_sameFieldsAreEqual() {
        // Given
        Comment c1 = new Comment(1, 1, "Name", "email@x.com", "Body");
        Comment c2 = new Comment(1, 1, "Name", "email@x.com", "Body");

        // Then
        assertThat(c1).isEqualTo(c2);
    }

    @Test
    @DisplayName("Comment postId links the comment to its parent post")
    void comment_postIdLinksToParentPost() {
        // Given: multiple comments for the same post
        Comment c1 = new Comment(1, 5, "First Comment", "a@b.com", "First");
        Comment c2 = new Comment(2, 5, "Second Comment", "c@d.com", "Second");

        // Then: both comments reference post id=5
        List<Comment> comments = List.of(c1, c2);
        assertThat(comments).allMatch(c -> c.postId().equals(5));
    }

    // ── User record ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("User record accessors return the values provided to the constructor")
    void user_accessorsReturnConstructorValues() {
        // Given / When
        User user = new User(3, "Alice Smith", "asmith", "alice@example.com");

        // Then
        assertThat(user.id()).isEqualTo(3);
        assertThat(user.name()).isEqualTo("Alice Smith");
        assertThat(user.username()).isEqualTo("asmith");
        assertThat(user.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("User record equality: two Users with the same fields are equal")
    void user_recordEquality_sameFieldsAreEqual() {
        // Given
        User u1 = new User(1, "Bob", "bob", "bob@example.com");
        User u2 = new User(1, "Bob", "bob", "bob@example.com");

        // Then
        assertThat(u1).isEqualTo(u2);
        assertThat(u1.hashCode()).isEqualTo(u2.hashCode());
    }

    @Test
    @DisplayName("User record equality: Users with different usernames are not equal")
    void user_recordEquality_differentUsernamesAreNotEqual() {
        // Given
        User u1 = new User(1, "Bob", "bob", "bob@example.com");
        User u2 = new User(1, "Bob", "robert", "bob@example.com");

        // Then: username differs so they are not equal
        assertThat(u1).isNotEqualTo(u2);
    }
}
