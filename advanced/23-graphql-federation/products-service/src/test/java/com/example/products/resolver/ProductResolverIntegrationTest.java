package com.example.products.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProductResolver} — the GraphQL endpoint layer.
 *
 * <p>These tests start the full Spring Boot application context
 * ({@link SpringBootTest.WebEnvironment#RANDOM_PORT}) and send real GraphQL
 * HTTP requests through the {@link HttpGraphQlTester}. No Docker containers
 * are needed here because the products-service uses an in-memory repository
 * — there are no external dependencies to containerize.
 *
 * <p>Why {@code @AutoConfigureHttpGraphQlTester}?
 * This annotation auto-configures an {@link HttpGraphQlTester} bean that posts
 * GraphQL operations to the running server's {@code /graphql} endpoint, just
 * like a real client would. It is the preferred way to test Spring for GraphQL
 * endpoints end-to-end.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@DisplayName("ProductResolver GraphQL integration tests")
class ProductResolverIntegrationTest {

    /**
     * Pre-configured tester that executes GraphQL operations via MockMvc.
     * Auto-injected by @AutoConfigureGraphQlTester (no real HTTP server needed).
     */
    @Autowired
    private GraphQlTester graphQlTester;

    // =========================================================================
    // products query
    // =========================================================================

    @Test
    @DisplayName("products query returns all 8 seeded products")
    void productsQuery_returnsAllProducts() {
        // language=GraphQL
        String query = """
                query {
                  products {
                    id
                    name
                    price
                    category
                    inStock
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()   // fails the test if the response contains any GraphQL errors
                .path("products")
                .entityList(Object.class)
                .hasSize(8);         // exactly 8 seeded products
    }

    @Test
    @DisplayName("products query returns products with non-blank names")
    void productsQuery_productsHaveNames() {
        // language=GraphQL
        String query = "query { products { id name } }";

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("products[*].name")
                .entityList(String.class)
                .satisfies(names -> names.forEach(name ->
                        assertThat(name).isNotBlank()));
    }

    // =========================================================================
    // product(id) query
    // =========================================================================

    @Test
    @DisplayName("product(id) returns the correct product for a known ID")
    void productByIdQuery_knownId_returnsProduct() {
        // language=GraphQL
        String query = """
                query {
                  product(id: "prod-1") {
                    id
                    name
                    category
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("product.id").entity(String.class).isEqualTo("prod-1")
                .path("product.name").entity(String.class).isEqualTo("Wireless Keyboard")
                .path("product.category").entity(String.class).isEqualTo("Electronics");
    }

    @Test
    @DisplayName("product(id) returns null for an unknown ID")
    void productByIdQuery_unknownId_returnsNull() {
        // language=GraphQL
        String query = """
                query {
                  product(id: "prod-999") {
                    id
                    name
                  }
                }
                """;

        // GraphQL null on a nullable field must NOT produce an error
        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("product").valueIsNull();
    }

    // =========================================================================
    // productsByCategory query
    // =========================================================================

    @Test
    @DisplayName("productsByCategory returns only Electronics products")
    void productsByCategoryQuery_electronics_returnsCorrectCount() {
        // language=GraphQL
        String query = """
                query {
                  productsByCategory(category: "Electronics") {
                    id
                    category
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("productsByCategory")
                .entityList(Object.class)
                .hasSize(5);   // 5 Electronics in seed data
    }

    @Test
    @DisplayName("productsByCategory returns empty list for unknown category")
    void productsByCategoryQuery_unknownCategory_returnsEmptyList() {
        // language=GraphQL
        String query = """
                query {
                  productsByCategory(category: "Clothing") {
                    id
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("productsByCategory")
                .entityList(Object.class)
                .hasSize(0);
    }

    // =========================================================================
    // productsInStock query
    // =========================================================================

    @Test
    @DisplayName("productsInStock returns only in-stock products")
    void productsInStockQuery_returnsOnlyInStockProducts() {
        // language=GraphQL
        String query = """
                query {
                  productsInStock {
                    id
                    inStock
                  }
                }
                """;

        // All returned products must have inStock == true
        graphQlTester.document(query)
                .execute()
                .errors().verify()
                .path("productsInStock[*].inStock")
                .entityList(Boolean.class)
                .satisfies(list -> list.forEach(inStock ->
                        assertThat(inStock).isTrue()));
    }

    @Test
    @DisplayName("productsInStock result is smaller than full product list")
    void productsInStockQuery_fewerThanAllProducts() {
        String allQuery = "query { products { id } }";
        String inStockQuery = "query { productsInStock { id } }";

        int allCount = graphQlTester.document(allQuery)
                .execute().errors().verify()
                .path("products").entityList(Object.class).get().size();

        int inStockCount = graphQlTester.document(inStockQuery)
                .execute().errors().verify()
                .path("productsInStock").entityList(Object.class).get().size();

        // prod-4 and prod-7 are out of stock, so inStockCount < allCount
        assertThat(inStockCount).isLessThan(allCount);
    }
}
