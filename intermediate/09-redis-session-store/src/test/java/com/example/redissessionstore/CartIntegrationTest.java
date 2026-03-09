package com.example.redissessionstore;

import com.example.redissessionstore.domain.CartItem;
import com.example.redissessionstore.dto.CartItemRequest;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the Redis session store.
 *
 * <p>This test class verifies end-to-end behaviour from the HTTP layer through
 * the service (with Spring Session's Redis persistence active) down to a real Redis
 * instance managed by Testcontainers. Key aspects:
 *
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded
 *       servlet container on a random port, so the full filter/serialisation stack
 *       is active — the same as in production.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up a Redis Docker container
 *       for the duration of the test class. The container is shared across all test
 *       methods to avoid restart overhead.</li>
 *   <li>{@link DynamicPropertySource} overrides the Redis host/port in the Spring
 *       Environment so Spring Boot connects to the Testcontainers-managed Redis
 *       rather than any locally running Redis.</li>
 *   <li>Session cookies are manually propagated between requests to simulate a
 *       real browser client maintaining session state across multiple HTTP calls.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Cart integration tests (Redis session store + REST API)")
class CartIntegrationTest {

    // ── Testcontainers Redis container ────────────────────────────────────────────

    /**
     * A Redis container shared by all tests in this class.
     *
     * <p>{@code static} is important: JUnit 5 + Testcontainers reuses a single
     * container instance for all test methods, avoiding repeated Docker start/stop
     * overhead. The container is stopped automatically after the last test.
     */
    @Container
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    /**
     * Register the container's dynamic host/port into the Spring Environment before
     * the application context is created.
     *
     * <p>This ensures {@code spring.data.redis.host} and {@code spring.data.redis.port}
     * point to the Testcontainers-managed Redis container, not any local Redis server.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /** The random port chosen by Spring Boot for the embedded servlet container. */
    @LocalServerPort
    int port;

    /**
     * {@link TestRestTemplate} is a convenience wrapper designed for integration tests:
     * it follows redirects and does not throw exceptions on 4xx/5xx responses.
     * Note: we do NOT use the @Autowired default instance because we need to manage
     * cookies manually. Instead we create request-specific header configurations.
     */
    @Autowired
    TestRestTemplate restTemplate;

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /** Base URL for all requests, built using the random server port. */
    private String baseUrl() {
        return "http://localhost:" + port + "/api/cart";
    }

    /**
     * Create request headers that include an existing session cookie.
     *
     * <p>In a real browser the cookie jar handles this automatically. In tests we
     * must pass the {@code SESSION} cookie manually to associate subsequent requests
     * with the same Redis session.
     *
     * @param sessionCookie the value of the {@code SESSION} cookie from a prior response
     * @return headers containing the {@code Cookie: SESSION=<value>} header
     */
    private HttpHeaders headersWithSession(String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "SESSION=" + sessionCookie);
        return headers;
    }

    /**
     * Extract the {@code SESSION} cookie value from a response's {@code Set-Cookie} header.
     *
     * @param response any HTTP response that may contain a {@code Set-Cookie} header
     * @return the session cookie value, or {@code null} if not present
     */
    private String extractSessionCookie(ResponseEntity<?> response) {
        List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null) return null;
        for (String header : setCookieHeaders) {
            // Cookie header format: SESSION=<value>; Path=/; HttpOnly
            if (header.startsWith("SESSION=")) {
                // Extract only the value part (before the first semicolon)
                return header.split(";")[0].substring("SESSION=".length());
            }
        }
        return null;
    }

    // ── Setup ─────────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // Nothing to reset — each test creates its own session via a fresh request
        // (no session cookie on the first request = new Redis session)
    }

    // ── GET /api/cart ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/cart returns 200 with empty cart for a new session")
    void getCart_returns200WithEmptyCart_forNewSession() {
        // When: first request, no session cookie → Spring Session creates a new Redis session
        ResponseEntity<CartItem[]> response = restTemplate.getForEntity(
                baseUrl(), CartItem[].class);

        // Then: 200 OK and an empty cart is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isEmpty();

        // And: a SESSION cookie was set by the server
        assertThat(extractSessionCookie(response)).isNotNull().isNotBlank();
    }

    // ── POST /api/cart/items ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/cart/items adds item and returns updated cart")
    void addItem_returns200WithUpdatedCart() {
        // Given: a valid item request
        CartItemRequest request = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 1);

        // When: add item to cart (no prior session cookie → new session)
        ResponseEntity<CartItem[]> response = restTemplate.postForEntity(
                baseUrl() + "/items", request, CartItem[].class);

        // Then: 200 OK and the cart contains the added item
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody()[0].getProductId()).isEqualTo("p1");
        assertThat(response.getBody()[0].getProductName()).isEqualTo("Laptop");
        assertThat(response.getBody()[0].getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/cart/items returns 400 for invalid request body")
    void addItem_returns400_whenInvalidRequest() {
        // Given: a request with a blank product ID (violates @NotBlank)
        CartItemRequest invalid = new CartItemRequest("", "Laptop", new BigDecimal("999.99"), 1);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/items", invalid, String.class);

        // Then: 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Session persistence across requests ───────────────────────────────────────

    @Test
    @DisplayName("Cart persists in Redis across multiple requests using the same session cookie")
    void cart_persistsAcrossRequests_usingSessionCookie() {
        // Step 1: Add a laptop to the cart (creates a new session in Redis)
        CartItemRequest laptopRequest = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 1);
        ResponseEntity<CartItem[]> addResponse = restTemplate.postForEntity(
                baseUrl() + "/items", laptopRequest, CartItem[].class);

        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Extract the SESSION cookie so subsequent requests use the same Redis session
        String sessionCookie = extractSessionCookie(addResponse);
        assertThat(sessionCookie).isNotNull();

        // Step 2: Retrieve the cart using the same session cookie
        HttpEntity<Void> getRequest = new HttpEntity<>(headersWithSession(sessionCookie));
        ResponseEntity<CartItem[]> getResponse = restTemplate.exchange(
                baseUrl(), HttpMethod.GET, getRequest, CartItem[].class);

        // Then: the cart still contains the laptop (Redis preserved it)
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull().hasSize(1);
        assertThat(getResponse.getBody()[0].getProductId()).isEqualTo("p1");
        assertThat(getResponse.getBody()[0].getProductName()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("Two clients with different session cookies have independent carts")
    void twoClients_haveIndependentCarts() {
        // Client A: add a laptop
        CartItemRequest laptopRequest = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 1);
        ResponseEntity<CartItem[]> clientAResponse = restTemplate.postForEntity(
                baseUrl() + "/items", laptopRequest, CartItem[].class);
        String sessionA = extractSessionCookie(clientAResponse);

        // Client B: add a mouse (in a separate session — no cookie passed)
        CartItemRequest mouseRequest = new CartItemRequest("p2", "Mouse", new BigDecimal("29.99"), 3);
        ResponseEntity<CartItem[]> clientBResponse = restTemplate.postForEntity(
                baseUrl() + "/items", mouseRequest, CartItem[].class);
        String sessionB = extractSessionCookie(clientBResponse);

        // Verify the two sessions are different
        assertThat(sessionA).isNotEqualTo(sessionB);

        // Check client A's cart only contains the laptop
        HttpEntity<Void> getA = new HttpEntity<>(headersWithSession(sessionA));
        ResponseEntity<CartItem[]> cartA = restTemplate.exchange(
                baseUrl(), HttpMethod.GET, getA, CartItem[].class);
        assertThat(cartA.getBody()).hasSize(1);
        assertThat(cartA.getBody()[0].getProductId()).isEqualTo("p1");

        // Check client B's cart only contains the mouse
        HttpEntity<Void> getB = new HttpEntity<>(headersWithSession(sessionB));
        ResponseEntity<CartItem[]> cartB = restTemplate.exchange(
                baseUrl(), HttpMethod.GET, getB, CartItem[].class);
        assertThat(cartB.getBody()).hasSize(1);
        assertThat(cartB.getBody()[0].getProductId()).isEqualTo("p2");
    }

    @Test
    @DisplayName("Adding the same product twice increments quantity rather than duplicating")
    void addItem_incrementsQuantity_whenSameProductAddedTwice() {
        // Step 1: Add 1 laptop
        CartItemRequest firstRequest = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 1);
        ResponseEntity<CartItem[]> firstAdd = restTemplate.postForEntity(
                baseUrl() + "/items", firstRequest, CartItem[].class);
        String sessionCookie = extractSessionCookie(firstAdd);

        // Step 2: Add 2 more laptops on the same session
        CartItemRequest secondRequest = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 2);
        HttpEntity<CartItemRequest> addMore = new HttpEntity<>(secondRequest, headersWithSession(sessionCookie));
        ResponseEntity<CartItem[]> secondAdd = restTemplate.exchange(
                baseUrl() + "/items", HttpMethod.POST, addMore, CartItem[].class);

        // Then: only one cart entry, but quantity is 3
        assertThat(secondAdd.getBody()).hasSize(1);
        assertThat(secondAdd.getBody()[0].getQuantity()).isEqualTo(3);
    }

    // ── DELETE /api/cart/items/{productId} ────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/cart/items/{productId} removes the item from the cart")
    void removeItem_removesProductFromCart() {
        // Step 1: Add a laptop
        CartItemRequest request = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 1);
        ResponseEntity<CartItem[]> addResponse = restTemplate.postForEntity(
                baseUrl() + "/items", request, CartItem[].class);
        String sessionCookie = extractSessionCookie(addResponse);

        // Step 2: Remove the laptop
        HttpEntity<Void> deleteRequest = new HttpEntity<>(headersWithSession(sessionCookie));
        ResponseEntity<CartItem[]> deleteResponse = restTemplate.exchange(
                baseUrl() + "/items/p1", HttpMethod.DELETE, deleteRequest, CartItem[].class);

        // Then: 200 OK and the cart is now empty
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResponse.getBody()).isNotNull().isEmpty();
    }

    // ── DELETE /api/cart ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/cart clears all items but session remains active")
    void clearCart_clearsAllItems_sessionRemainsActive() {
        // Step 1: Add two items
        CartItemRequest laptopRequest = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 1);
        ResponseEntity<CartItem[]> addResponse = restTemplate.postForEntity(
                baseUrl() + "/items", laptopRequest, CartItem[].class);
        String sessionCookie = extractSessionCookie(addResponse);

        CartItemRequest mouseRequest = new CartItemRequest("p2", "Mouse", new BigDecimal("29.99"), 2);
        HttpEntity<CartItemRequest> addMouse = new HttpEntity<>(mouseRequest, headersWithSession(sessionCookie));
        restTemplate.exchange(baseUrl() + "/items", HttpMethod.POST, addMouse, CartItem[].class);

        // Step 2: Clear the cart
        HttpEntity<Void> clearRequest = new HttpEntity<>(headersWithSession(sessionCookie));
        ResponseEntity<Void> clearResponse = restTemplate.exchange(
                baseUrl(), HttpMethod.DELETE, clearRequest, Void.class);

        // Then: 204 No Content
        assertThat(clearResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // And: the cart is now empty (session is still active, just the cart was cleared)
        HttpEntity<Void> getRequest = new HttpEntity<>(headersWithSession(sessionCookie));
        ResponseEntity<CartItem[]> getResponse = restTemplate.exchange(
                baseUrl(), HttpMethod.GET, getRequest, CartItem[].class);
        assertThat(getResponse.getBody()).isNotNull().isEmpty();
    }

    // ── DELETE /api/cart/session ──────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/cart/session invalidates the session entirely")
    void invalidateSession_removesSessionFromRedis() {
        // Step 1: Create a session by adding an item
        CartItemRequest request = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 1);
        ResponseEntity<CartItem[]> addResponse = restTemplate.postForEntity(
                baseUrl() + "/items", request, CartItem[].class);
        String sessionCookie = extractSessionCookie(addResponse);
        assertThat(sessionCookie).isNotNull();

        // Step 2: Invalidate the session
        HttpEntity<Void> invalidateRequest = new HttpEntity<>(headersWithSession(sessionCookie));
        ResponseEntity<Void> invalidateResponse = restTemplate.exchange(
                baseUrl() + "/session", HttpMethod.DELETE, invalidateRequest, Void.class);

        // Then: 204 No Content
        assertThat(invalidateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Step 3: Using the old session cookie now results in an empty cart
        // (the session was deleted from Redis so Spring Session creates a fresh one)
        HttpEntity<Void> getRequest = new HttpEntity<>(headersWithSession(sessionCookie));
        ResponseEntity<CartItem[]> getResponse = restTemplate.exchange(
                baseUrl(), HttpMethod.GET, getRequest, CartItem[].class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull().isEmpty();
    }

    // ── GET /api/cart/total ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/cart/total returns correct sum for cart items")
    void getCartTotal_returnsCorrectSum() {
        // Step 1: Add a laptop (999.99 * 1 = 999.99)
        CartItemRequest laptopRequest = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 1);
        ResponseEntity<CartItem[]> addLaptop = restTemplate.postForEntity(
                baseUrl() + "/items", laptopRequest, CartItem[].class);
        String sessionCookie = extractSessionCookie(addLaptop);

        // Step 2: Add a mouse (29.99 * 2 = 59.98)
        CartItemRequest mouseRequest = new CartItemRequest("p2", "Mouse", new BigDecimal("29.99"), 2);
        HttpEntity<CartItemRequest> addMouse = new HttpEntity<>(mouseRequest, headersWithSession(sessionCookie));
        restTemplate.exchange(baseUrl() + "/items", HttpMethod.POST, addMouse, CartItem[].class);

        // Step 3: Get the total
        HttpEntity<Void> getTotal = new HttpEntity<>(headersWithSession(sessionCookie));
        ResponseEntity<Map> totalResponse = restTemplate.exchange(
                baseUrl() + "/total", HttpMethod.GET, getTotal, Map.class);

        // Then: 200 OK and total = 999.99 + 59.98 = 1059.97
        assertThat(totalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(totalResponse.getBody()).isNotNull();
        // JSON numbers come back as Double in raw Map deserialization
        Object total = totalResponse.getBody().get("total");
        assertThat(new BigDecimal(total.toString())).isEqualByComparingTo("1059.97");
    }

    // ── GET /api/cart/session-info ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/cart/session-info returns session metadata")
    void getSessionInfo_returnsSessionMetadata() {
        // When: request session info (creates a new session)
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/session-info", Map.class);

        // Then: 200 OK with expected keys in the response body
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("sessionId");
        assertThat(response.getBody()).containsKey("cartSize");
        assertThat(response.getBody()).containsKey("cartTotal");
        assertThat(response.getBody()).containsKey("maxInactiveIntervalSeconds");

        // sessionId should be a non-blank string
        assertThat(response.getBody().get("sessionId")).isNotNull();
        assertThat(response.getBody().get("sessionId").toString()).isNotBlank();

        // maxInactiveIntervalSeconds should equal the value configured in SessionConfig (1800)
        assertThat(response.getBody().get("maxInactiveIntervalSeconds"))
                .isEqualTo(1800);
    }
}
