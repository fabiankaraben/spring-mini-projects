package com.example.feignclientintegration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full integration tests for the Feign Client Integration API.
 *
 * <p>These tests verify end-to-end behaviour from the HTTP layer through the
 * service layer and Feign client down to a mocked upstream API (WireMock).
 *
 * <p><strong>Key aspects of this test setup:</strong>
 * <ul>
 *   <li>{@link SpringBootTest} with {@code MOCK} web environment starts the full
 *       Spring application context with a mock servlet environment. The complete
 *       filter chain, exception handlers, and serialisation stack are active.</li>
 *   <li>{@link AutoConfigureMockMvc} injects {@link MockMvc}, which performs HTTP
 *       requests against the mock servlet without starting a real HTTP server.</li>
 *   <li>{@link WireMockServer} runs as an in-process HTTP server on a random port,
 *       acting as the fake JSONPlaceholder API. The Feign client's base URL is
 *       overridden to point at WireMock via {@link DynamicPropertySource}.</li>
 *   <li>WireMock is started once for the class ({@link BeforeAll}) and stopped once
 *       ({@link AfterAll}), while stubs are reset between each test ({@link BeforeEach})
 *       to ensure test isolation.</li>
 * </ul>
 *
 * <p><strong>WireMock vs Testcontainers WireMock:</strong><br>
 * We use {@link WireMockServer} (in-process) rather than a WireMock Docker container
 * because:
 * <ul>
 *   <li>It starts and stops faster (no Docker pull/start overhead).</li>
 *   <li>It runs in the same JVM, making stub setup trivial (no HTTP calls to configure).</li>
 *   <li>WireMock standalone JAR already includes the server — no extra image needed.</li>
 * </ul>
 *
 * <p><strong>Why is this still a "full integration test" with Testcontainers?</strong><br>
 * This project has no database or other Docker dependency. The "integration" aspect
 * here is that we start the real Spring Boot application context (not a slice like
 * {@code @WebMvcTest}), meaning all beans — Feign client proxy, service, controller,
 * exception handler — are wired together and tested as a system. WireMock provides
 * the deterministic external boundary for the Feign HTTP calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("PostController integration tests (Feign + WireMock)")
class PostControllerIntegrationTest {

    // ── WireMock server (in-process) ──────────────────────────────────────────────

    /**
     * WireMock server that acts as the fake JSONPlaceholder upstream API.
     *
     * <p>{@code static} ensures a single WireMock instance is shared across all
     * test methods in this class. Starting it in {@link BeforeAll} avoids the
     * overhead of spinning up a new HTTP server for each test method.
     *
     * <p>Port 0 tells WireMock to bind to a random available port, preventing
     * port conflicts with other processes (e.g., the app itself or other tests).
     */
    static WireMockServer wireMockServer;

    /**
     * Start the WireMock server before any tests run.
     *
     * <p>Port 0 = random available port. WireMock finds and binds to it automatically.
     * We read the actual port with {@code wireMockServer.port()} afterwards.
     */
    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        // Configure the WireMock static client to point at our in-process server
        configureFor("localhost", wireMockServer.port());
    }

    /**
     * Stop the WireMock server after all tests in this class have finished.
     */
    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    /**
     * Override the Feign client's base URL property before the Spring context
     * is created. {@link DynamicPropertySource} injects properties into the
     * Spring {@link org.springframework.core.env.Environment} before context startup.
     *
     * <p>This makes the {@code @FeignClient(url = "${jsonplaceholder.base-url}")}
     * annotation resolve to the WireMock server URL instead of the real API.
     * Without this override, Feign would attempt to call jsonplaceholder.typicode.com
     * during tests, making tests slow and flaky (network-dependent).
     *
     * @param registry the property registry injected by Spring Boot test infrastructure
     */
    @DynamicPropertySource
    static void overrideFeignBaseUrl(DynamicPropertyRegistry registry) {
        // Supplier is called after wireMockServer.start() so the port is known
        registry.add("jsonplaceholder.base-url",
                () -> "http://localhost:" + wireMockServer.port());
    }

    /**
     * Reset all WireMock stubs and recorded requests between tests.
     *
     * <p>Without this reset, a stub registered in test A would remain active during
     * test B, causing unexpected behaviour (e.g., test B accidentally matching a
     * stub configured for a different scenario).
     */
    @BeforeEach
    void resetWireMockStubs() {
        resetAllRequests();
        wireMockServer.resetAll();
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /**
     * {@link MockMvc} performs HTTP requests against the mock servlet started by
     * {@link SpringBootTest}. Spring Boot auto-configures this when
     * {@link AutoConfigureMockMvc} is present.
     */
    @Autowired
    MockMvc mockMvc;

    // ── GET /api/posts ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/posts returns 200 with all posts from the upstream API")
    void getAllPosts_returns200WithAllPosts() throws Exception {
        // Given: WireMock stubs GET /posts to return a JSON array with two posts
        stubFor(WireMock.get(urlEqualTo("/posts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id":1,"userId":1,"title":"First Post","body":"First body"},
                                  {"id":2,"userId":1,"title":"Second Post","body":"Second body"}
                                ]
                                """)));

        // When / Then: our API returns both posts
        mockMvc.perform(get("/api/posts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].title", is("First Post")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].title", is("Second Post")));
    }

    @Test
    @DisplayName("GET /api/posts?userId=1 returns 200 with only posts by that user")
    void getPostsByUser_returns200WithFilteredPosts() throws Exception {
        // Given: WireMock stubs GET /posts?userId=1 to return user 1's posts
        stubFor(WireMock.get(urlPathEqualTo("/posts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id":1,"userId":1,"title":"User 1 Post","body":"Body"}
                                ]
                                """)));

        // When / Then
        mockMvc.perform(get("/api/posts")
                        .param("userId", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is(1)));
    }

    // ── GET /api/posts/{id} ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/posts/{id} returns 200 with the post when it exists")
    void getPostById_returns200WithPost_whenFound() throws Exception {
        // Given: WireMock stubs GET /posts/1 to return a single post
        stubFor(WireMock.get(urlEqualTo("/posts/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"userId":1,"title":"My Post","body":"Post body"}
                                """)));

        // When / Then
        mockMvc.perform(get("/api/posts/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("My Post")))
                .andExpect(jsonPath("$.body", is("Post body")))
                .andExpect(jsonPath("$.userId", is(1)));
    }

    @Test
    @DisplayName("GET /api/posts/{id} returns 404 when the upstream API returns 404")
    void getPostById_returns404_whenUpstreamReturns404() throws Exception {
        // Given: WireMock simulates a 404 from the upstream API
        stubFor(WireMock.get(urlEqualTo("/posts/999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        // When / Then: our GlobalExceptionHandler maps FeignException.NotFound → 404
        mockMvc.perform(get("/api/posts/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/posts/{id}/with-comments ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/posts/{id}/with-comments returns 200 with post and comments combined")
    void getPostWithComments_returns200WithEnrichedPost() throws Exception {
        // Given: WireMock stubs both Feign calls (fan-out pattern)
        stubFor(WireMock.get(urlEqualTo("/posts/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"userId":1,"title":"My Post","body":"Post body"}
                                """)));

        stubFor(WireMock.get(urlEqualTo("/posts/1/comments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id":1,"postId":1,"name":"Comment A","email":"a@b.com","body":"Nice post!"},
                                  {"id":2,"postId":1,"name":"Comment B","email":"c@d.com","body":"Thanks!"}
                                ]
                                """)));

        // When / Then: the response combines both into one JSON object
        mockMvc.perform(get("/api/posts/1/with-comments")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.id", is(1)))
                .andExpect(jsonPath("$.post.title", is("My Post")))
                .andExpect(jsonPath("$.comments", hasSize(2)))
                .andExpect(jsonPath("$.comments[0].email", is("a@b.com")))
                .andExpect(jsonPath("$.comments[1].email", is("c@d.com")));
    }

    @Test
    @DisplayName("GET /api/posts/{id}/with-comments returns empty comments list when post has none")
    void getPostWithComments_returnsEmptyComments_whenNoneExist() throws Exception {
        // Given: post exists but has no comments
        stubFor(WireMock.get(urlEqualTo("/posts/5"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":5,"userId":2,"title":"Post 5","body":"Body 5"}
                                """)));

        stubFor(WireMock.get(urlEqualTo("/posts/5/comments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        // When / Then
        mockMvc.perform(get("/api/posts/5/with-comments")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.id", is(5)))
                .andExpect(jsonPath("$.comments", hasSize(0)));
    }

    // ── POST /api/posts ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/posts returns 201 with the created post from the upstream API")
    void createPost_returns201WithCreatedPost() throws Exception {
        // Given: WireMock stubs POST /posts to return a created post (id=101 per JSONPlaceholder)
        stubFor(WireMock.post(urlEqualTo("/posts"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":101,"userId":1,"title":"New Post","body":"New content"}
                                """)));

        String requestBody = """
                {
                  "userId": 1,
                  "title": "New Post",
                  "body": "New content"
                }
                """;

        // When / Then: our API returns 201 with the created post
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(101)))
                .andExpect(jsonPath("$.title", is("New Post")))
                .andExpect(jsonPath("$.userId", is(1)));
    }

    @Test
    @DisplayName("POST /api/posts returns 400 when the title is blank")
    void createPost_returns400_whenTitleIsBlank() throws Exception {
        // Given: an invalid request body (blank title)
        String invalidBody = """
                {
                  "userId": 1,
                  "title": "",
                  "body": "Some content"
                }
                """;

        // When / Then: Bean Validation triggers before Feign is called; no WireMock stub needed
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/posts returns 400 when userId is null")
    void createPost_returns400_whenUserIdIsNull() throws Exception {
        // Given: userId is missing (null)
        String invalidBody = """
                {
                  "title": "Some title",
                  "body": "Some content"
                }
                """;

        // When / Then: @NotNull on userId triggers validation failure
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/posts returns 400 when body is blank")
    void createPost_returns400_whenBodyIsBlank() throws Exception {
        // Given: blank body text
        String invalidBody = """
                {
                  "userId": 1,
                  "title": "Some title",
                  "body": ""
                }
                """;

        // When / Then: @NotBlank on body triggers validation failure
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/posts/{postId}/comments ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/posts/{postId}/comments returns 200 with comments for the post")
    void getCommentsByPost_returns200WithComments() throws Exception {
        // Given: WireMock stubs GET /posts/1/comments
        stubFor(WireMock.get(urlEqualTo("/posts/1/comments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id":1,"postId":1,"name":"Alice","email":"alice@ex.com","body":"Great!"},
                                  {"id":2,"postId":1,"name":"Bob","email":"bob@ex.com","body":"Agreed."}
                                ]
                                """)));

        // When / Then
        mockMvc.perform(get("/api/posts/1/comments")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].postId", is(1)))
                .andExpect(jsonPath("$[0].email", is("alice@ex.com")))
                .andExpect(jsonPath("$[1].name", is("Bob")));
    }

    // ── GET /api/users ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users returns 200 with all users from the upstream API")
    void getAllUsers_returns200WithAllUsers() throws Exception {
        // Given: WireMock stubs GET /users
        stubFor(WireMock.get(urlEqualTo("/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id":1,"name":"John Doe","username":"johndoe","email":"john@example.com"},
                                  {"id":2,"name":"Jane Doe","username":"janedoe","email":"jane@example.com"}
                                ]
                                """)));

        // When / Then
        mockMvc.perform(get("/api/users")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("John Doe")))
                .andExpect(jsonPath("$[0].username", is("johndoe")))
                .andExpect(jsonPath("$[1].email", is("jane@example.com")));
    }

    // ── GET /api/users/{id} ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users/{id} returns 200 with the user when found")
    void getUserById_returns200WithUser_whenFound() throws Exception {
        // Given: WireMock stubs GET /users/1
        stubFor(WireMock.get(urlEqualTo("/users/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"name":"John Doe","username":"johndoe","email":"john@example.com"}
                                """)));

        // When / Then
        mockMvc.perform(get("/api/users/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.username", is("johndoe")))
                .andExpect(jsonPath("$.email", is("john@example.com")));
    }

    @Test
    @DisplayName("GET /api/users/{id} returns 404 when the upstream API returns 404")
    void getUserById_returns404_whenUpstreamReturns404() throws Exception {
        // Given: WireMock simulates the upstream returning 404 for user 999
        stubFor(WireMock.get(urlEqualTo("/users/999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        // When / Then: GlobalExceptionHandler maps Feign 404 → our 404
        mockMvc.perform(get("/api/users/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
