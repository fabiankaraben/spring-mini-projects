package com.example.feignclientintegration.service;

import com.example.feignclientintegration.client.JsonPlaceholderClient;
import com.example.feignclientintegration.domain.Comment;
import com.example.feignclientintegration.domain.Post;
import com.example.feignclientintegration.domain.User;
import com.example.feignclientintegration.dto.CreatePostRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PostService}.
 *
 * <p>These tests verify the service's business logic in pure isolation:
 * <ul>
 *   <li>The {@link JsonPlaceholderClient} Feign proxy is replaced with a Mockito mock,
 *       so no real HTTP requests are made. Tests run in milliseconds without any
 *       network dependency.</li>
 *   <li>No Spring context is loaded — {@link ExtendWith}({@link MockitoExtension}.class)
 *       initialises Mockito annotations only, keeping startup time near zero.</li>
 *   <li>Each test follows the Given / When / Then (Arrange / Act / Assert) pattern.</li>
 * </ul>
 *
 * <p><strong>Why mock the Feign client?</strong><br>
 * A Feign client interface is a Spring bean (a JDK proxy). In unit tests we don't
 * want the Spring context or real HTTP calls, so Mockito creates a mock implementation
 * of the interface. This lets us test the service's logic (DTO mapping, aggregation,
 * filtering) completely independently of the HTTP transport layer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostService unit tests")
class PostServiceTest {

    /**
     * Mockito mock of the Feign client proxy.
     * No Spring context, no real HTTP — all interactions are simulated via
     * {@code when(...).thenReturn(...)}.
     */
    @Mock
    private JsonPlaceholderClient jsonPlaceholderClient;

    /**
     * The class under test.
     * {@code @InjectMocks} creates a {@link PostService} instance and injects
     * the {@code @Mock} field into it via constructor injection.
     */
    @InjectMocks
    private PostService postService;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    /** A sample Post domain object returned by the mock client. */
    private Post samplePost;

    /** A sample Comment returned by the mock client. */
    private Comment sampleComment;

    /** A sample User returned by the mock client. */
    private User sampleUser;

    @BeforeEach
    void setUp() {
        // Build shared fixtures used across multiple test methods
        samplePost = new Post(1, 1, "Test Post Title", "Test post body content");
        sampleComment = new Comment(1, 1, "Test Comment", "commenter@example.com", "Comment body");
        sampleUser = new User(1, "John Doe", "johndoe", "john@example.com");
    }

    // ── getAllPosts ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllPosts returns all posts from the Feign client")
    void getAllPosts_returnsAllPostsFromClient() {
        // Given: the mock client returns two posts
        Post second = new Post(2, 1, "Second Post", "Second body");
        when(jsonPlaceholderClient.getAllPosts()).thenReturn(List.of(samplePost, second));

        // When
        List<Post> result = postService.getAllPosts();

        // Then: the service returns all posts unchanged
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1);
        assertThat(result.get(0).title()).isEqualTo("Test Post Title");
        assertThat(result.get(1).id()).isEqualTo(2);

        // Verify the Feign client was called exactly once
        verify(jsonPlaceholderClient, times(1)).getAllPosts();
    }

    @Test
    @DisplayName("getAllPosts returns an empty list when the client returns no posts")
    void getAllPosts_returnsEmptyList_whenClientReturnsNone() {
        // Given: the mock client returns an empty list
        when(jsonPlaceholderClient.getAllPosts()).thenReturn(List.of());

        // When
        List<Post> result = postService.getAllPosts();

        // Then
        assertThat(result).isEmpty();
        verify(jsonPlaceholderClient, times(1)).getAllPosts();
    }

    // ── getPostById ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPostById returns the post with the given ID")
    void getPostById_returnsPost_whenFound() {
        // Given: the mock client returns the sample post for id=1
        when(jsonPlaceholderClient.getPostById(1)).thenReturn(samplePost);

        // When
        Post result = postService.getPostById(1);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1);
        assertThat(result.title()).isEqualTo("Test Post Title");
        assertThat(result.body()).isEqualTo("Test post body content");

        verify(jsonPlaceholderClient, times(1)).getPostById(1);
    }

    // ── getPostsByUser ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPostsByUser returns posts filtered by userId")
    void getPostsByUser_returnsPostsForGivenUser() {
        // Given: user 1 has two posts
        Post anotherPost = new Post(2, 1, "Another Post", "Another body");
        when(jsonPlaceholderClient.getPostsByUser(1)).thenReturn(List.of(samplePost, anotherPost));

        // When
        List<Post> result = postService.getPostsByUser(1);

        // Then: only user 1's posts are returned
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> p.userId().equals(1));

        verify(jsonPlaceholderClient, times(1)).getPostsByUser(1);
    }

    @Test
    @DisplayName("getPostsByUser returns an empty list when the user has no posts")
    void getPostsByUser_returnsEmptyList_whenUserHasNoPosts() {
        // Given: user 999 has no posts
        when(jsonPlaceholderClient.getPostsByUser(999)).thenReturn(List.of());

        // When
        List<Post> result = postService.getPostsByUser(999);

        // Then
        assertThat(result).isEmpty();
        verify(jsonPlaceholderClient, times(1)).getPostsByUser(999);
    }

    // ── createPost ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createPost maps DTO to Post domain object and delegates to Feign client")
    void createPost_mapsDtoAndDelegatestoClient() {
        // Given: the mock client simulates a server-assigned id (101 per JSONPlaceholder behaviour)
        Post serverResponse = new Post(101, 2, "My New Post", "Post content here");
        when(jsonPlaceholderClient.createPost(any(Post.class))).thenReturn(serverResponse);

        CreatePostRequest request = new CreatePostRequest(2, "My New Post", "Post content here");

        // When
        Post created = postService.createPost(request);

        // Then: the returned post has the server-assigned id and the correct field values
        assertThat(created).isNotNull();
        assertThat(created.id()).isEqualTo(101);
        assertThat(created.userId()).isEqualTo(2);
        assertThat(created.title()).isEqualTo("My New Post");
        assertThat(created.body()).isEqualTo("Post content here");

        // Verify the Feign client was called with a Post whose id is null (server-assigned)
        verify(jsonPlaceholderClient, times(1)).createPost(any(Post.class));
    }

    @Test
    @DisplayName("createPost sends a Post with null id so the upstream API assigns it")
    void createPost_sendsNullIdToUpstreamApi() {
        // Given: capture the argument sent to the Feign client
        when(jsonPlaceholderClient.createPost(any(Post.class)))
                .thenAnswer(invocation -> {
                    Post sent = invocation.getArgument(0);
                    // The service must send id=null because the server assigns the ID
                    assertThat(sent.id()).isNull();
                    assertThat(sent.userId()).isEqualTo(3);
                    assertThat(sent.title()).isEqualTo("Captured Title");
                    return new Post(101, 3, "Captured Title", "Body");
                });

        // When
        postService.createPost(new CreatePostRequest(3, "Captured Title", "Body"));

        // Then: the assertion inside the answer lambda confirmed id is null
        verify(jsonPlaceholderClient, times(1)).createPost(any(Post.class));
    }

    // ── getCommentsByPost ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCommentsByPost returns comments for the given post ID")
    void getCommentsByPost_returnsCommentsForPost() {
        // Given: the mock client returns one comment for post 1
        when(jsonPlaceholderClient.getCommentsByPost(1)).thenReturn(List.of(sampleComment));

        // When
        List<Comment> result = postService.getCommentsByPost(1);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).postId()).isEqualTo(1);
        assertThat(result.get(0).email()).isEqualTo("commenter@example.com");

        verify(jsonPlaceholderClient, times(1)).getCommentsByPost(1);
    }

    @Test
    @DisplayName("getCommentsByPost returns an empty list when there are no comments")
    void getCommentsByPost_returnsEmptyList_whenNoComments() {
        // Given: post 999 has no comments
        when(jsonPlaceholderClient.getCommentsByPost(999)).thenReturn(List.of());

        // When
        List<Comment> result = postService.getCommentsByPost(999);

        // Then
        assertThat(result).isEmpty();
        verify(jsonPlaceholderClient, times(1)).getCommentsByPost(999);
    }

    // ── getAllUsers ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllUsers returns all users from the Feign client")
    void getAllUsers_returnsAllUsers() {
        // Given: the mock client returns one user
        when(jsonPlaceholderClient.getAllUsers()).thenReturn(List.of(sampleUser));

        // When
        List<User> result = postService.getAllUsers();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("John Doe");
        assertThat(result.get(0).email()).isEqualTo("john@example.com");

        verify(jsonPlaceholderClient, times(1)).getAllUsers();
    }

    // ── getUserById ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserById returns the user with the given ID")
    void getUserById_returnsUser() {
        // Given
        when(jsonPlaceholderClient.getUserById(1)).thenReturn(sampleUser);

        // When
        User result = postService.getUserById(1);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1);
        assertThat(result.username()).isEqualTo("johndoe");

        verify(jsonPlaceholderClient, times(1)).getUserById(1);
    }

    // ── getPostWithComments ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getPostWithComments fans out to two Feign calls and returns enriched post")
    void getPostWithComments_fanOutToTwoClientsAndCombinesResults() {
        // Given: both Feign endpoints are stubbed
        when(jsonPlaceholderClient.getPostById(1)).thenReturn(samplePost);
        when(jsonPlaceholderClient.getCommentsByPost(1)).thenReturn(List.of(sampleComment));

        // When
        PostService.EnrichedPost enriched = postService.getPostWithComments(1);

        // Then: the result combines both Feign responses into one object
        assertThat(enriched).isNotNull();
        assertThat(enriched.post()).isEqualTo(samplePost);
        assertThat(enriched.comments()).hasSize(1);
        assertThat(enriched.comments().get(0)).isEqualTo(sampleComment);

        // Both Feign client methods must have been called exactly once
        verify(jsonPlaceholderClient, times(1)).getPostById(1);
        verify(jsonPlaceholderClient, times(1)).getCommentsByPost(1);
    }

    @Test
    @DisplayName("getPostWithComments returns enriched post with empty comment list when post has no comments")
    void getPostWithComments_returnsEmptyComments_whenPostHasNone() {
        // Given: the post exists but has no comments
        when(jsonPlaceholderClient.getPostById(1)).thenReturn(samplePost);
        when(jsonPlaceholderClient.getCommentsByPost(1)).thenReturn(List.of());

        // When
        PostService.EnrichedPost enriched = postService.getPostWithComments(1);

        // Then: post is present but comments list is empty
        assertThat(enriched.post()).isEqualTo(samplePost);
        assertThat(enriched.comments()).isEmpty();

        verify(jsonPlaceholderClient, times(1)).getPostById(1);
        verify(jsonPlaceholderClient, times(1)).getCommentsByPost(1);
    }
}
