package com.example.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the gateway using embedded WireMock servers.
 *
 * <p>This test class starts the full Spring Boot gateway application context
 * on a random port and configures two embedded WireMock servers — one per
 * subgraph service — to stub HTTP GraphQL responses. This approach:
 *
 * <ul>
 *   <li>Tests the real gateway HTTP stack (WebClient → GraphQL endpoint)</li>
 *   <li>Needs NO running Docker containers — WireMock is purely in-process</li>
 *   <li>Demonstrates the full federation data flow end-to-end</li>
 * </ul>
 *
 * <p>WireMock strategy:
 * <ol>
 *   <li>Two {@link WireMockServer} instances start on random OS-assigned ports
 *       before all tests ({@link BeforeAll}).</li>
 *   <li>{@link DynamicPropertySource} points the gateway's
 *       {@code federation.products-url} / {@code federation.reviews-url} at
 *       the WireMock ports so the real {@code ProductsClient} / {@code ReviewsClient}
 *       call the stubs.</li>
 *   <li>Each test registers stubs in {@link BeforeEach} and resets them
 *       after each test to ensure isolation.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@DisplayName("Gateway integration tests (WireMock)")
class GatewayIntegrationTest {

    // -------------------------------------------------------------------------
    // Static WireMock servers — shared across all test methods
    // -------------------------------------------------------------------------

    /**
     * Embedded WireMock server simulating the products-service subgraph.
     * Uses a random port (0) to avoid port conflicts.
     */
    static WireMockServer productsMock =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    /**
     * Embedded WireMock server simulating the reviews-service subgraph.
     * Uses a random port (0) to avoid port conflicts.
     */
    static WireMockServer reviewsMock =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    /**
     * Start both WireMock servers once before any test in this class runs.
     * @BeforeAll requires the method to be static.
     */
    @BeforeAll
    static void startWireMock() {
        productsMock.start();
        reviewsMock.start();
    }

    /**
     * Stop both WireMock servers after all tests in this class have finished.
     */
    @AfterAll
    static void stopWireMock() {
        productsMock.stop();
        reviewsMock.stop();
    }

    /**
     * Registers Spring application properties dynamically at test startup.
     *
     * <p>{@link DynamicPropertySource} is the standard Spring Test mechanism
     * for injecting runtime-computed values (like random WireMock ports) into
     * the application context's {@link org.springframework.core.env.Environment}.
     * These override the defaults in application.yml.
     */
    @DynamicPropertySource
    static void configureFederationUrls(DynamicPropertyRegistry registry) {
        // Point the gateway's WebClient base URLs at the local WireMock servers
        registry.add("federation.products-url",
                () -> "http://localhost:" + productsMock.port());
        registry.add("federation.reviews-url",
                () -> "http://localhost:" + reviewsMock.port());
    }

    /**
     * Reset all WireMock stubs before each test to guarantee test isolation.
     * Without this, stubs from one test would bleed into the next.
     */
    @BeforeEach
    void resetStubs() {
        productsMock.resetAll();
        reviewsMock.resetAll();
    }

    /** GraphQL tester auto-configured against the running gateway server. */
    @Autowired
    private HttpGraphQlTester graphQlTester;

    // =========================================================================
    // Helper: stub builders
    // =========================================================================

    /**
     * Returns a WireMock stub JSON response body for the {@code products} query.
     * The gateway sends POST /graphql with any body — we match on any POST
     * and return a well-formed GraphQL response.
     */
    private static String productsResponse() {
        return """
                {
                  "data": {
                    "products": [
                      {
                        "id": "prod-1",
                        "name": "Wireless Keyboard",
                        "description": "Compact Bluetooth keyboard",
                        "price": 79.99,
                        "category": "Electronics",
                        "inStock": true
                      },
                      {
                        "id": "prod-2",
                        "name": "Ergonomic Mouse",
                        "description": "Vertical ergonomic mouse",
                        "price": 49.99,
                        "category": "Electronics",
                        "inStock": true
                      }
                    ]
                  }
                }
                """;
    }

    /**
     * Returns a WireMock stub JSON response body for a single {@code product} query.
     */
    private static String singleProductResponse() {
        return """
                {
                  "data": {
                    "product": {
                      "id": "prod-1",
                      "name": "Wireless Keyboard",
                      "description": "Compact Bluetooth keyboard",
                      "price": 79.99,
                      "category": "Electronics",
                      "inStock": true
                    }
                  }
                }
                """;
    }

    /** Stub response for product not found (null data.product). */
    private static String productNotFoundResponse() {
        return """
                {
                  "data": {
                    "product": null
                  }
                }
                """;
    }

    /**
     * Returns a WireMock stub JSON response body for the {@code reviews} query.
     */
    private static String reviewsResponse() {
        return """
                {
                  "data": {
                    "reviews": [
                      {
                        "id": "rev-1",
                        "productId": "prod-1",
                        "author": "Alice",
                        "body": "Great keyboard!",
                        "rating": 5
                      },
                      {
                        "id": "rev-2",
                        "productId": "prod-1",
                        "author": "Bob",
                        "body": "Battery life is short.",
                        "rating": 4
                      }
                    ]
                  }
                }
                """;
    }

    /**
     * Returns WireMock stub for reviewsByProduct — reviews only for prod-1.
     */
    private static String reviewsByProductResponse() {
        return """
                {
                  "data": {
                    "reviewsByProduct": [
                      {
                        "id": "rev-1",
                        "productId": "prod-1",
                        "author": "Alice",
                        "body": "Great keyboard!",
                        "rating": 5
                      },
                      {
                        "id": "rev-2",
                        "productId": "prod-1",
                        "author": "Bob",
                        "body": "Battery life is short.",
                        "rating": 4
                      }
                    ]
                  }
                }
                """;
    }

    // =========================================================================
    // Tests: products query delegation
    // =========================================================================

    @Test
    @DisplayName("products query: gateway delegates to products-service and returns products")
    void productsQuery_delegatesToProductsService() {
        // Stub: any POST to /graphql on the products mock returns our list
        productsMock.stubFor(post(urlEqualTo("/graphql"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(productsResponse())));

        String query = """
                query {
                  products {
                    id
                    name
                    category
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("products")
                .entityList(Object.class)
                .hasSize(2);

        // Verify the products-service was actually called
        productsMock.verify(1, postRequestedFor(urlEqualTo("/graphql")));
        // Reviews service must NOT have been called for a pure products query
        reviewsMock.verify(0, postRequestedFor(anyUrl()));
    }

    // =========================================================================
    // Tests: product(id) query delegation
    // =========================================================================

    @Test
    @DisplayName("product(id) query: gateway returns the correct product")
    void productByIdQuery_returnsCorrectProduct() {
        productsMock.stubFor(post(urlEqualTo("/graphql"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(singleProductResponse())));

        String query = """
                query {
                  product(id: "prod-1") {
                    id
                    name
                    price
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("product.id").entity(String.class).isEqualTo("prod-1")
                .path("product.name").entity(String.class).isEqualTo("Wireless Keyboard");
    }

    @Test
    @DisplayName("product(id) query: gateway returns null when product not found")
    void productByIdQuery_notFound_returnsNull() {
        productsMock.stubFor(post(urlEqualTo("/graphql"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(productNotFoundResponse())));

        String query = """
                query {
                  product(id: "prod-999") {
                    id
                    name
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("product").valueIsNull();
    }

    // =========================================================================
    // Tests: reviews query delegation
    // =========================================================================

    @Test
    @DisplayName("reviews query: gateway delegates to reviews-service and returns reviews")
    void reviewsQuery_delegatesToReviewsService() {
        reviewsMock.stubFor(post(urlEqualTo("/graphql"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(reviewsResponse())));

        String query = """
                query {
                  reviews {
                    id
                    productId
                    author
                    rating
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("reviews")
                .entityList(Object.class)
                .hasSize(2);

        reviewsMock.verify(1, postRequestedFor(urlEqualTo("/graphql")));
        productsMock.verify(0, postRequestedFor(anyUrl()));
    }

    // =========================================================================
    // Tests: productWithReviews — THE core federation query
    // =========================================================================

    @Test
    @DisplayName("productWithReviews: gateway stitches product and reviews from both services")
    void productWithReviews_stitchesDataFromBothServices() {
        // Stub products-service — product(id) query
        productsMock.stubFor(post(urlEqualTo("/graphql"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(singleProductResponse())));

        // Stub reviews-service — reviewsByProduct query
        reviewsMock.stubFor(post(urlEqualTo("/graphql"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(reviewsByProductResponse())));

        // Execute the core federation query — data comes from BOTH services
        String query = """
                query {
                  productWithReviews(productId: "prod-1") {
                    product {
                      id
                      name
                      price
                    }
                    reviews {
                      id
                      author
                      rating
                    }
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                // Product data comes from products-service
                .path("productWithReviews.product.id")
                .entity(String.class).isEqualTo("prod-1")
                .path("productWithReviews.product.name")
                .entity(String.class).isEqualTo("Wireless Keyboard")
                // Reviews data comes from reviews-service
                .path("productWithReviews.reviews")
                .entityList(Object.class).hasSize(2);

        // Assert BOTH services were called exactly once — this is the federation proof
        productsMock.verify(1, postRequestedFor(urlEqualTo("/graphql")));
        reviewsMock.verify(1, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    @DisplayName("productWithReviews: returns null when product does not exist")
    void productWithReviews_productNotFound_returnsNull() {
        // Products-service returns null product
        productsMock.stubFor(post(urlEqualTo("/graphql"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(productNotFoundResponse())));

        String query = """
                query {
                  productWithReviews(productId: "prod-999") {
                    product { id name }
                    reviews { id }
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("productWithReviews").valueIsNull();

        // Products-service is called; reviews-service is NOT (short-circuit)
        productsMock.verify(1, postRequestedFor(urlEqualTo("/graphql")));
        reviewsMock.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("reviews query: returns correct author names from reviews-service stub")
    void reviewsQuery_returnsCorrectAuthorNames() {
        reviewsMock.stubFor(post(urlEqualTo("/graphql"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(reviewsResponse())));

        String query = "query { reviews { id author rating } }";

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("reviews[*].author")
                .entityList(String.class)
                .satisfies(authors -> {
                    assertThat(authors).contains("Alice", "Bob");
                });
    }
}
