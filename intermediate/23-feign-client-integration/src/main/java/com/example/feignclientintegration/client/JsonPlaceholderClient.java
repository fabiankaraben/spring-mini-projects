package com.example.feignclientintegration.client;

import com.example.feignclientintegration.domain.Comment;
import com.example.feignclientintegration.domain.Post;
import com.example.feignclientintegration.domain.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * OpenFeign declarative HTTP client for the JSONPlaceholder REST API.
 *
 * <p><strong>How Feign works:</strong><br>
 * At application startup, Spring Cloud OpenFeign scans for interfaces annotated
 * with {@link FeignClient} (because {@code @EnableFeignClients} is on the main
 * class). For each interface it finds, it creates a JDK dynamic proxy that:
 * <ol>
 *   <li>Reads the {@code url} from the {@code @FeignClient} annotation (or from
 *       the Spring Environment if a logical service name is used with a discovery
 *       client).</li>
 *   <li>Maps each interface method to an HTTP request using the Spring MVC
 *       annotations ({@code @GetMapping}, {@code @PostMapping}, etc.).</li>
 *   <li>Serialises method arguments to request parameters or JSON bodies using
 *       Jackson (the default Feign encoder/decoder when Spring MVC is on the
 *       classpath).</li>
 *   <li>Deserialises the HTTP response body back into the method's return type.</li>
 * </ol>
 *
 * <p><strong>Configuration:</strong><br>
 * The {@code url} attribute resolves the {@code jsonplaceholder.base-url} property
 * defined in {@code application.yml}. This makes the base URL easily overridable for
 * tests (WireMock) and different deployment environments without code changes.
 *
 * <p><strong>Feign vs RestTemplate vs WebClient:</strong>
 * <ul>
 *   <li>{@code RestTemplate} — imperative, must manually build URIs and parse responses.</li>
 *   <li>{@code WebClient} — reactive/non-blocking; great for high-throughput async work.</li>
 *   <li>{@code Feign} — declarative; zero boilerplate, interface-first, best for
 *       microservice-to-microservice HTTP calls where simplicity is preferred.</li>
 * </ul>
 */
@FeignClient(
        name = "jsonplaceholder",           // logical name used for Spring context bean naming
        url = "${jsonplaceholder.base-url}" // base URL injected from application.yml
)
public interface JsonPlaceholderClient {

    // ── Post endpoints ────────────────────────────────────────────────────────────

    /**
     * Retrieve all posts from the upstream API.
     *
     * <p>Feign translates this to: {@code GET <base-url>/posts}
     * The response is a JSON array which Feign/Jackson deserialises into a
     * {@code List<Post>} automatically.
     *
     * @return list of all posts
     */
    @GetMapping("/posts")
    List<Post> getAllPosts();

    /**
     * Retrieve a single post by its ID.
     *
     * <p>Feign translates this to: {@code GET <base-url>/posts/{id}}
     * The {@code @PathVariable} value replaces the {@code {id}} placeholder in
     * the URL path segment.
     *
     * @param id the post's unique identifier
     * @return the post with the given ID
     */
    @GetMapping("/posts/{id}")
    Post getPostById(@PathVariable("id") Integer id);

    /**
     * Retrieve all posts authored by a specific user.
     *
     * <p>Feign translates this to: {@code GET <base-url>/posts?userId={userId}}
     * The {@code @RequestParam} annotation tells Feign to append this value as a
     * query parameter rather than a path segment.
     *
     * @param userId the ID of the user whose posts to retrieve
     * @return list of posts authored by the given user
     */
    @GetMapping("/posts")
    List<Post> getPostsByUser(@RequestParam("userId") Integer userId);

    /**
     * Create a new post on the upstream API.
     *
     * <p>Feign translates this to:
     * {@code POST <base-url>/posts} with a JSON body.
     * The {@code @RequestBody} annotation instructs Feign to serialise the
     * {@link Post} argument to JSON and set the {@code Content-Type: application/json}
     * header. JSONPlaceholder returns the created post with a server-assigned ID.
     *
     * <p>Note: JSONPlaceholder is a read-only mock API; the returned post has
     * {@code id = 101} regardless of what we send (the data is not actually persisted).
     *
     * @param post the post data to create
     * @return the created post as returned by the upstream API
     */
    @PostMapping("/posts")
    Post createPost(@RequestBody Post post);

    // ── Comment endpoints ─────────────────────────────────────────────────────────

    /**
     * Retrieve all comments for a specific post.
     *
     * <p>Feign translates this to: {@code GET <base-url>/posts/{postId}/comments}
     * This demonstrates using a nested/sub-resource URL pattern common in REST APIs.
     *
     * @param postId the ID of the post whose comments to retrieve
     * @return list of comments for the given post
     */
    @GetMapping("/posts/{postId}/comments")
    List<Comment> getCommentsByPost(@PathVariable("postId") Integer postId);

    // ── User endpoints ────────────────────────────────────────────────────────────

    /**
     * Retrieve all users from the upstream API.
     *
     * <p>Feign translates this to: {@code GET <base-url>/users}
     *
     * @return list of all users
     */
    @GetMapping("/users")
    List<User> getAllUsers();

    /**
     * Retrieve a single user by their ID.
     *
     * <p>Feign translates this to: {@code GET <base-url>/users/{id}}
     *
     * @param id the user's unique identifier
     * @return the user with the given ID
     */
    @GetMapping("/users/{id}")
    User getUserById(@PathVariable("id") Integer id);
}
