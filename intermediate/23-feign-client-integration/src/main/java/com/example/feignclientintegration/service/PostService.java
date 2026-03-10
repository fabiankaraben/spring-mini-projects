package com.example.feignclientintegration.service;

import com.example.feignclientintegration.client.JsonPlaceholderClient;
import com.example.feignclientintegration.domain.Comment;
import com.example.feignclientintegration.domain.Post;
import com.example.feignclientintegration.domain.User;
import com.example.feignclientintegration.dto.CreatePostRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service layer for post, comment, and user operations.
 *
 * <p>This class sits between the HTTP layer ({@link com.example.feignclientintegration.controller.PostController})
 * and the Feign client ({@link JsonPlaceholderClient}). Key responsibilities:
 * <ul>
 *   <li>Map request DTOs ({@link CreatePostRequest}) to domain objects ({@link Post}).</li>
 *   <li>Encapsulate business logic — e.g., filtering posts by user, building enriched
 *       response objects that combine data from multiple upstream endpoints.</li>
 *   <li>Keep the controller thin: HTTP concerns stay in the controller,
 *       business logic stays here.</li>
 * </ul>
 *
 * <p><strong>Why a service layer over a direct Feign client call?</strong><br>
 * In a real application the service can:
 * <ul>
 *   <li>Combine calls to multiple Feign clients (fan-out and aggregate).</li>
 *   <li>Apply caching ({@code @Cacheable}) independent of the HTTP layer.</li>
 *   <li>Add retry or circuit-breaker logic transparently to the controller.</li>
 *   <li>Be unit-tested with a mock Feign client, without needing Spring context.</li>
 * </ul>
 */
@Service
public class PostService {

    /**
     * The Feign client proxy injected by Spring Cloud OpenFeign.
     * At runtime this is a JDK dynamic proxy that converts method calls
     * to HTTP requests directed at the JSONPlaceholder base URL.
     */
    private final JsonPlaceholderClient jsonPlaceholderClient;

    /**
     * Constructor injection — makes the dependency explicit and enables
     * unit testing without a Spring context (just pass a mock client).
     *
     * @param jsonPlaceholderClient the OpenFeign proxy for JSONPlaceholder
     */
    public PostService(JsonPlaceholderClient jsonPlaceholderClient) {
        this.jsonPlaceholderClient = jsonPlaceholderClient;
    }

    // ── Post operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all posts from the upstream API.
     *
     * <p>Delegates directly to the Feign client. The Feign proxy performs:
     * {@code GET https://jsonplaceholder.typicode.com/posts}
     *
     * @return list of all posts
     */
    public List<Post> getAllPosts() {
        return jsonPlaceholderClient.getAllPosts();
    }

    /**
     * Retrieve a single post by its ID.
     *
     * <p>Delegates directly to the Feign client. The Feign proxy performs:
     * {@code GET https://jsonplaceholder.typicode.com/posts/{id}}
     *
     * @param id the post's unique identifier
     * @return the post with the given ID
     */
    public Post getPostById(Integer id) {
        return jsonPlaceholderClient.getPostById(id);
    }

    /**
     * Retrieve all posts authored by a specific user.
     *
     * <p>Feign performs: {@code GET /posts?userId={userId}}
     * The upstream API filters posts server-side using the query parameter.
     *
     * @param userId the ID of the user whose posts to retrieve
     * @return list of posts authored by the given user
     */
    public List<Post> getPostsByUser(Integer userId) {
        return jsonPlaceholderClient.getPostsByUser(userId);
    }

    /**
     * Create a new post on the upstream API.
     *
     * <p>Maps the {@link CreatePostRequest} DTO to a {@link Post} domain object
     * (with {@code id = null} since the server assigns IDs), then calls the Feign
     * client which performs:
     * {@code POST https://jsonplaceholder.typicode.com/posts}
     * with a JSON body.
     *
     * <p>JSONPlaceholder always returns {@code id = 101} since the data is not
     * actually persisted. A real API would return the server-assigned ID.
     *
     * @param request the validated request DTO from the HTTP layer
     * @return the created post as returned by the upstream API
     */
    public Post createPost(CreatePostRequest request) {
        // Map the DTO to a domain object; id is null (server-assigned on create)
        Post post = new Post(
                null,           // id — assigned by the upstream API
                request.userId(),
                request.title(),
                request.body()
        );
        return jsonPlaceholderClient.createPost(post);
    }

    // ── Comment operations ────────────────────────────────────────────────────────

    /**
     * Retrieve all comments for a specific post.
     *
     * <p>Feign performs: {@code GET /posts/{postId}/comments}
     * This demonstrates calling a nested resource URL.
     *
     * @param postId the ID of the post whose comments to retrieve
     * @return list of comments for the given post
     */
    public List<Comment> getCommentsByPost(Integer postId) {
        return jsonPlaceholderClient.getCommentsByPost(postId);
    }

    // ── User operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all users from the upstream API.
     *
     * <p>Feign performs: {@code GET /users}
     *
     * @return list of all users
     */
    public List<User> getAllUsers() {
        return jsonPlaceholderClient.getAllUsers();
    }

    /**
     * Retrieve a single user by their ID.
     *
     * <p>Feign performs: {@code GET /users/{id}}
     *
     * @param id the user's unique identifier
     * @return the user with the given ID
     */
    public User getUserById(Integer id) {
        return jsonPlaceholderClient.getUserById(id);
    }

    // ── Aggregation: multi-client fan-out ─────────────────────────────────────────

    /**
     * Retrieve a post together with all its comments in one service call.
     *
     * <p>This method demonstrates one of the key strengths of the service layer:
     * it can <em>fan out</em> to multiple Feign endpoints and combine the results.
     * The controller gets a single, fully-enriched object without any HTTP plumbing.
     *
     * <p>Two sequential Feign calls are made:
     * <ol>
     *   <li>{@code GET /posts/{id}} — fetch the post.</li>
     *   <li>{@code GET /posts/{id}/comments} — fetch its comments.</li>
     * </ol>
     *
     * @param postId the post ID to enrich
     * @return an {@link EnrichedPost} combining the post and its comments
     */
    public EnrichedPost getPostWithComments(Integer postId) {
        Post post = jsonPlaceholderClient.getPostById(postId);
        List<Comment> comments = jsonPlaceholderClient.getCommentsByPost(postId);
        return new EnrichedPost(post, comments);
    }

    /**
     * A simple aggregate response DTO combining a post with its comments.
     *
     * <p>Defined as a static nested record so it lives close to the service
     * logic that creates it. It is used as a controller response body and
     * Jackson serialises it to JSON automatically.
     *
     * @param post     the post data
     * @param comments all comments belonging to this post
     */
    public record EnrichedPost(Post post, List<Comment> comments) {}
}
