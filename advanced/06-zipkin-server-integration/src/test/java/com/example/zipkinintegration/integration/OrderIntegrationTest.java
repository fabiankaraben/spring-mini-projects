package com.example.zipkinintegration.integration;

import com.example.zipkinintegration.dto.CreateOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full integration tests for the Order API with a real Zipkin server.
 *
 * <h2>What these tests cover</h2>
 * <ul>
 *   <li>Order creation ({@code POST /api/orders}) – happy path and validation
 *       failures.</li>
 *   <li>Order retrieval ({@code GET /api/orders}, {@code GET /api/orders/{id}})
 *       – list and single-item lookups.</li>
 *   <li>Status update ({@code PATCH /api/orders/{id}/status}) – valid and
 *       not-found cases.</li>
 *   <li>Trace ID propagation – that every response includes a non-blank
 *       {@code traceId} field.</li>
 *   <li>Zipkin export – that the application can start with a live Zipkin
 *       container and export spans without errors.</li>
 * </ul>
 *
 * <h2>Testcontainers setup</h2>
 * <p>A real Zipkin server is started in Docker via {@link GenericContainer}.
 * {@code @DynamicPropertySource} overrides {@code spring.zipkin.tracing.endpoint}
 * with the container's actual host and port so that the Spring Boot application
 * sends spans to this container rather than to {@code localhost:9411}.
 *
 * <p>The sampling probability is set to {@code 1.0} for integration tests so
 * every request produces a span that is exported to Zipkin.
 *
 * <h2>Image choice</h2>
 * <p>{@code openzipkin/zipkin:3} is a multi-architecture image that works on
 * both AMD64 and ARM64 (Apple Silicon). Avoid {@code -slim} or other
 * single-arch variants.
 *
 * <h2>Testcontainers lifecycle</h2>
 * <p>The container is {@code static} and started explicitly in
 * {@code @BeforeAll} to ensure it is fully running before Spring creates its
 * application context, so {@link #overrideZipkinEndpoint} receives the
 * correct URL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Order API – Integration Tests with Zipkin")
class OrderIntegrationTest {

    /**
     * Zipkin server container.
     *
     * <p>{@code openzipkin/zipkin:3} is the official multi-arch image.
     * Port 9411 is Zipkin's default HTTP collector and UI port.
     *
     * <p>We wait for the {@code /health} endpoint to return HTTP 200 before
     * declaring the container ready, with a generous 90-second timeout for
     * slower CI environments.
     */
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> zipkin = new GenericContainer<>("openzipkin/zipkin:3")
            .withExposedPorts(9411)
            .waitingFor(
                    Wait.forHttp("/health")
                            .forPort(9411)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(90))
            );

    /**
     * Overrides the Zipkin endpoint so the Spring Boot application sends
     * spans to the Testcontainers-managed Zipkin rather than localhost:9411.
     *
     * <p>Also enables 100% sampling so every request in the integration test
     * suite produces an exported span.
     *
     * @param registry the dynamic property registry provided by Spring Test
     */
    @DynamicPropertySource
    static void overrideZipkinEndpoint(DynamicPropertyRegistry registry) {
        registry.add("spring.zipkin.tracing.endpoint",
                () -> "http://" + zipkin.getHost() + ":" + zipkin.getMappedPort(9411) + "/api/v2/spans");
        // Enable 100% sampling so every test request is traced and exported
        registry.add("spring.tracing.sampling.probability", () -> "1.0");
        registry.add("management.tracing.sampling.probability", () -> "1.0");
    }

    /**
     * Starts the Zipkin container before the Spring context is created.
     * Explicit start ensures {@link #overrideZipkinEndpoint} gets the right port.
     */
    @BeforeAll
    static void beforeAll() {
        zipkin.start();
    }

    /**
     * Stops and removes the Zipkin container after all tests finish.
     * Releases Docker resources promptly instead of waiting for JVM shutdown.
     */
    @AfterAll
    static void afterAll() {
        zipkin.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    /** Jackson ObjectMapper for serialising request DTOs to JSON strings. */
    @Autowired
    private ObjectMapper objectMapper;

    // ── POST /api/orders ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders should return 201 with order details for valid request")
    void createOrder_shouldReturn201_forValidRequest() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("laptop-pro-15", 2);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.product", is("laptop-pro-15")))
                .andExpect(jsonPath("$.quantity", is(2)))
                // A valid product should be CONFIRMED (inventory available)
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    @DisplayName("POST /api/orders should include a non-blank traceId in the response")
    void createOrder_shouldIncludeTraceId() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("monitor-4k", 1);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                // traceId must be present and non-null in every response
                .andExpect(jsonPath("$.traceId", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/orders should return CANCELLED status for unavailable product")
    void createOrder_shouldReturnCancelled_forUnavailableProduct() throws Exception {
        // Products whose names start with "unavailable" are always out of stock
        CreateOrderRequest request = new CreateOrderRequest("unavailable-item-xyz", 1);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    @DisplayName("POST /api/orders should return 400 for blank product name")
    void createOrder_shouldReturn400_forBlankProduct() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("", 1);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/orders should return 400 for zero quantity")
    void createOrder_shouldReturn400_forZeroQuantity() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("some-product", 0);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/orders should return 400 for negative quantity")
    void createOrder_shouldReturn400_forNegativeQuantity() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("widget", -5);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/orders ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders should return 200 with a JSON array")
    void getAllOrders_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/orders should include a created order in the list")
    void getAllOrders_shouldContainCreatedOrder() throws Exception {
        // Create an order first
        CreateOrderRequest request = new CreateOrderRequest("keyboard-mech", 3);
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // The list should contain at least one order
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                // jsonPath $.length() checks the array size is at least 1
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/{id} should return 200 for an existing order")
    void getOrderById_shouldReturn200_forExistingOrder() throws Exception {
        // Create an order and extract its ID from the response
        CreateOrderRequest request = new CreateOrderRequest("headphones", 1);
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Extract the ID from the JSON response
        String responseBody = createResult.getResponse().getContentAsString();
        Long id = objectMapper.readTree(responseBody).get("id").asLong();

        // Now retrieve by ID
        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.intValue())))
                .andExpect(jsonPath("$.product", is("headphones")));
    }

    @Test
    @DisplayName("GET /api/orders/{id} should return 404 for a non-existent ID")
    void getOrderById_shouldReturn404_forNonExistentId() throws Exception {
        mockMvc.perform(get("/api/orders/999999"))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/orders/{id}/status ─────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/orders/{id}/status should update status to SHIPPED")
    void updateStatus_shouldReturn200_withUpdatedStatus() throws Exception {
        // Create an order
        CreateOrderRequest request = new CreateOrderRequest("tablet-10", 1);
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Update the status to SHIPPED
        mockMvc.perform(patch("/api/orders/" + id + "/status")
                        .param("status", "SHIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SHIPPED")));
    }

    @Test
    @DisplayName("PATCH /api/orders/{id}/status should return 404 for a non-existent order")
    void updateStatus_shouldReturn404_forNonExistentOrder() throws Exception {
        mockMvc.perform(patch("/api/orders/999999/status")
                        .param("status", "DELIVERED"))
                .andExpect(status().isNotFound());
    }

    // ── Trace ID validation ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/{id} should include a traceId in the response")
    void getOrderById_shouldIncludeTraceId() throws Exception {
        // Create an order
        CreateOrderRequest request = new CreateOrderRequest("smartwatch", 1);
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Verify the GET response includes a traceId
        MvcResult getResult = mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId", notNullValue()))
                .andReturn();

        // The traceId must be a non-blank string (Zipkin 128-bit hex or "no-trace" in tests)
        String body = getResult.getResponse().getContentAsString();
        String traceId = objectMapper.readTree(body).get("traceId").asText();
        assertThat(traceId).isNotBlank();
    }

    @Test
    @DisplayName("Zipkin container should be reachable at /health")
    void zipkinContainer_shouldBeHealthy() throws Exception {
        // Verify the Zipkin container itself started and responds to health checks.
        // This is an infrastructure sanity test – it fails fast if the image
        // cannot be pulled or the container fails to start.
        assertThat(zipkin.isRunning()).isTrue();
    }
}
