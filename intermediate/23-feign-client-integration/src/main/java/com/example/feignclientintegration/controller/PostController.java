package com.example.feignclientintegration.controller;

import com.example.feignclientintegration.domain.Comment;
import com.example.feignclientintegration.domain.Post;
import com.example.feignclientintegration.domain.User;
import com.example.feignclientintegration.dto.CreatePostRequest;
import com.example.feignclientintegration.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes endpoints backed by OpenFeign HTTP clients.
 *
 * <p>This controller is intentionally thin: it handles only HTTP concerns
 * (request/response mapping, status codes, validation triggering). All
 * business logic and Feign interactions are delegated to {@link PostService}.
 *
 * <p>API routes exposed by this controller:
 * <pre>
 *   GET  /api/posts                  – all posts
 *   GET  /api/posts/{id}             – post by ID
 *   GET  /api/posts/{id}/with-comments – post + comments (aggregated)
 *   GET  /api/posts?userId={id}      – posts by user
 *   POST /api/posts                  – create a post
 *   GET  /api/posts/{id}/comments    – comments for a post
 *   GET  /api/users                  – all users
 *   GET  /api/users/{id}             – user by ID
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService postService;

    /**
     * Constructor injection ensures the dependency is explicit and testable
     * without a full Spring context (using {@code @WebMvcTest} with a mock service).
     *
     * @param postService service containing the business logic
     */
    public PostController(PostService postService) {
        this.postService = postService;
    }

    // ── Post endpoints ────────────────────────────────────────────────────────────

    /**
     * Retrieve all posts.
     *
     * <p>The Feign client calls {@code GET /posts} on the upstream API.
     *
     * @return 200 OK with a JSON array of all posts
     */
    @GetMapping("/posts")
    public ResponseEntity<List<Post>> getAllPosts(
            @RequestParam(value = "userId", required = false) Integer userId) {
        // If a userId query param is present, filter posts by that user;
        // otherwise return all posts. A single endpoint handles both cases.
        List<Post> posts = (userId != null)
                ? postService.getPostsByUser(userId)
                : postService.getAllPosts();
        return ResponseEntity.ok(posts);
    }

    /**
     * Retrieve a single post by its ID.
     *
     * <p>The Feign client calls {@code GET /posts/{id}} on the upstream API.
     *
     * @param id the post ID path variable
     * @return 200 OK with the post JSON
     */
    @GetMapping("/posts/{id}")
    public ResponseEntity<Post> getPostById(@PathVariable("id") Integer id) {
        Post post = postService.getPostById(id);
        return ResponseEntity.ok(post);
    }

    /**
     * Retrieve a post together with all its comments (aggregated response).
     *
     * <p>Demonstrates a service-layer fan-out: the service calls two Feign
     * endpoints and merges the results before returning a single JSON object.
     *
     * @param id the post ID path variable
     * @return 200 OK with the enriched post JSON (post + comments)
     */
    @GetMapping("/posts/{id}/with-comments")
    public ResponseEntity<PostService.EnrichedPost> getPostWithComments(
            @PathVariable("id") Integer id) {
        PostService.EnrichedPost enriched = postService.getPostWithComments(id);
        return ResponseEntity.ok(enriched);
    }

    /**
     * Create a new post on the upstream API.
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body before the
     * method body executes. If validation fails, Spring returns 400 Bad Request
     * automatically (handled by {@link com.example.feignclientintegration.exception.GlobalExceptionHandler}).
     *
     * @param request the validated request body
     * @return 201 Created with the created post JSON
     */
    @PostMapping("/posts")
    public ResponseEntity<Post> createPost(@Valid @RequestBody CreatePostRequest request) {
        Post created = postService.createPost(request);
        // HTTP 201 Created is the correct status for successful resource creation
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── Comment endpoints ─────────────────────────────────────────────────────────

    /**
     * Retrieve all comments for a given post.
     *
     * <p>The Feign client calls {@code GET /posts/{postId}/comments}.
     *
     * @param postId the post ID path variable
     * @return 200 OK with a JSON array of comments
     */
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<Comment>> getCommentsByPost(
            @PathVariable("postId") Integer postId) {
        List<Comment> comments = postService.getCommentsByPost(postId);
        return ResponseEntity.ok(comments);
    }

    // ── User endpoints ────────────────────────────────────────────────────────────

    /**
     * Retrieve all users.
     *
     * <p>The Feign client calls {@code GET /users} on the upstream API.
     *
     * @return 200 OK with a JSON array of all users
     */
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = postService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * Retrieve a single user by their ID.
     *
     * <p>The Feign client calls {@code GET /users/{id}} on the upstream API.
     *
     * @param id the user ID path variable
     * @return 200 OK with the user JSON
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable("id") Integer id) {
        User user = postService.getUserById(id);
        return ResponseEntity.ok(user);
    }
}
