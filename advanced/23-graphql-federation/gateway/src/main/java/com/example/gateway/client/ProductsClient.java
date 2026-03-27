package com.example.gateway.client;

import com.example.gateway.model.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP client that delegates GraphQL queries to the products-service subgraph.
 *
 * <p>This client encapsulates all communication with the products-service. The
 * gateway calls these methods when it needs to resolve Product-related fields
 * in the unified supergraph schema.
 *
 * <p>Communication protocol:
 * <ul>
 *   <li>Transport: HTTP POST to {@code <products-url>/graphql}</li>
 *   <li>Request body: {@code {"query": "...", "variables": {...}}}</li>
 *   <li>Response body: {@code {"data": {...}, "errors": [...]}}</li>
 * </ul>
 *
 * <p>{@link WebClient} is used (even though the gateway is MVC-based) because
 * it supports both blocking and non-blocking usage and has better ergonomics
 * for JSON parsing than {@code RestTemplate}.
 */
@Component
public class ProductsClient {

    /**
     * WebClient pointed at the products-service base URL.
     * The base URL is injected from application.yml (federation.products-url).
     */
    private final WebClient webClient;

    /** Jackson ObjectMapper for manually extracting the GraphQL data node. */
    private final ObjectMapper objectMapper;

    /**
     * Constructs the client with a WebClient pointed at the products-service URL.
     *
     * @param productsUrl  base URL of the products-service (e.g. http://localhost:8081)
     * @param objectMapper Jackson ObjectMapper bean provided by Spring Boot auto-config
     */
    public ProductsClient(
            @Value("${federation.products-url}") String productsUrl,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl(productsUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches all products from the products-service.
     *
     * <p>Sends the following GraphQL operation:
     * <pre>
     * query { products { id name description price category inStock } }
     * </pre>
     *
     * @return list of all products (never null; returns empty list on error)
     */
    public List<Product> fetchAllProducts() {
        // GraphQL query string — all scalar fields of Product
        String query = """
                query {
                  products {
                    id name description price category inStock
                  }
                }
                """;

        // Execute the HTTP POST and extract the data.products array
        JsonNode data = executeQuery(query, Map.of());
        return parseList(data.path("products"),
                new TypeReference<List<Product>>() {});
    }

    /**
     * Fetches a single product by ID from the products-service.
     *
     * <p>Sends the following GraphQL operation:
     * <pre>
     * query($id: ID!) { product(id: $id) { id name description price category inStock } }
     * </pre>
     *
     * @param id the product ID to look up
     * @return Optional containing the product if found, or empty
     */
    public Optional<Product> fetchProductById(String id) {
        String query = """
                query($id: ID!) {
                  product(id: $id) {
                    id name description price category inStock
                  }
                }
                """;

        JsonNode data = executeQuery(query, Map.of("id", id));
        JsonNode productNode = data.path("product");
        if (productNode.isMissingNode() || productNode.isNull()) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.convertValue(productNode, Product.class));
    }

    /**
     * Fetches all products in the given category from the products-service.
     *
     * @param category category name to filter by (case-insensitive server-side)
     * @return list of matching products
     */
    public List<Product> fetchProductsByCategory(String category) {
        String query = """
                query($category: String!) {
                  productsByCategory(category: $category) {
                    id name description price category inStock
                  }
                }
                """;

        JsonNode data = executeQuery(query, Map.of("category", category));
        return parseList(data.path("productsByCategory"),
                new TypeReference<List<Product>>() {});
    }

    /**
     * Fetches all in-stock products from the products-service.
     *
     * @return list of products with inStock == true
     */
    public List<Product> fetchProductsInStock() {
        String query = """
                query {
                  productsInStock {
                    id name description price category inStock
                  }
                }
                """;

        JsonNode data = executeQuery(query, Map.of());
        return parseList(data.path("productsInStock"),
                new TypeReference<List<Product>>() {});
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Executes a GraphQL operation against the products-service and returns
     * the {@code data} node of the response.
     *
     * <p>GraphQL errors are NOT thrown as exceptions in this demo — they are
     * logged and an empty data node is returned so the gateway can handle
     * partial results gracefully. In production you would inspect the
     * {@code errors} array and propagate them properly.
     *
     * @param query     GraphQL query/mutation string
     * @param variables map of variable name → value (use {@link Map#of()} if none)
     * @return the {@code data} JsonNode from the response (never null)
     */
    private JsonNode executeQuery(String query, Map<String, Object> variables) {
        // Build the GraphQL request body: {"query": "...", "variables": {...}}
        Map<String, Object> requestBody = Map.of(
                "query", query,
                "variables", variables
        );

        // POST to /graphql and parse the response synchronously (.block())
        // Using block() is acceptable here because the gateway uses Spring MVC
        // (blocking). In a fully reactive gateway you would return Mono/Flux.
        String responseBody = webClient.post()
                .uri("/graphql")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            // The GraphQL response always has a "data" top-level field
            return root.path("data");
        } catch (Exception e) {
            // Return an empty object node so callers get empty lists / empty optionals
            return objectMapper.createObjectNode();
        }
    }

    /**
     * Converts a JsonNode array to a typed Java List using the given TypeReference.
     *
     * @param node          the JSON array node to convert
     * @param typeReference Jackson type reference for the target list type
     * @param <T>           element type of the list
     * @return parsed list, or an empty list if the node is missing/null
     */
    private <T> List<T> parseList(JsonNode node, TypeReference<List<T>> typeReference) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(node, typeReference);
        } catch (Exception e) {
            return List.of();
        }
    }
}
