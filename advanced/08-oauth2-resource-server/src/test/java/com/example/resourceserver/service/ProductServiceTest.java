package com.example.resourceserver.service;

import com.example.resourceserver.domain.Product;
import com.example.resourceserver.dto.CreateProductRequest;
import com.example.resourceserver.dto.UpdateProductRequest;
import com.example.resourceserver.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>These tests verify the service's business logic in isolation from the
 * database. We mock {@link ProductRepository} to return controlled data and
 * verify that the service delegates correctly and maps DTOs to domain objects.
 *
 * <p><b>Testing approach:</b>
 * <ul>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} activates Mockito without
 *       loading a Spring context — tests run fast.</li>
 *   <li>The repository is mocked with {@code @Mock} — no real in-memory store
 *       is involved, giving complete control over what the repository returns.</li>
 *   <li>We verify that the service maps DTO fields correctly to domain objects
 *       and returns the expected results.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService — unit tests")
class ProductServiceTest {

    /**
     * Mocked repository — no actual in-memory store is involved.
     * Mockito injects this mock automatically via @ExtendWith(MockitoExtension.class).
     */
    @Mock
    private ProductRepository productRepository;

    /** The service under test, constructed with the mocked repository. */
    private ProductService productService;

    /** A reusable sample product used across multiple tests. */
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);

        // Build a sample product to return from mocked repository calls
        sampleProduct = new Product(
                1L,
                "Wireless Headphones",
                "Noise-cancelling headphones",
                new BigDecimal("149.99"),
                "ELECTRONICS",
                75,
                Instant.now(),
                Instant.now()
        );
    }

    // =========================================================================
    // getAllProducts()
    // =========================================================================

    @Nested
    @DisplayName("getAllProducts()")
    class GetAllProductsTests {

        /**
         * Verifies that getAllProducts() delegates to the repository and returns
         * whatever the repository returns.
         */
        @Test
        @DisplayName("returns all products from repository")
        void returnsAllProductsFromRepository() {
            when(productRepository.findAll()).thenReturn(List.of(sampleProduct));

            List<Product> result = productService.getAllProducts();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Wireless Headphones");
        }

        /**
         * Verifies that an empty repository returns an empty list (not null).
         */
        @Test
        @DisplayName("returns empty list when repository is empty")
        void returnsEmptyListWhenRepositoryIsEmpty() {
            when(productRepository.findAll()).thenReturn(List.of());

            List<Product> result = productService.getAllProducts();

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // getProductById()
    // =========================================================================

    @Nested
    @DisplayName("getProductById()")
    class GetProductByIdTests {

        /**
         * Verifies that getProductById() returns the product wrapped in Optional.present
         * when the repository finds it.
         */
        @Test
        @DisplayName("returns Optional.present when product exists")
        void returnsProductWhenFound() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

            Optional<Product> result = productService.getProductById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
        }

        /**
         * Verifies that getProductById() returns Optional.empty when the product
         * does not exist — the caller should handle 404.
         */
        @Test
        @DisplayName("returns Optional.empty when product does not exist")
        void returnsEmptyWhenNotFound() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            Optional<Product> result = productService.getProductById(99L);

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // getProductsByCategory()
    // =========================================================================

    @Nested
    @DisplayName("getProductsByCategory()")
    class GetProductsByCategoryTests {

        /**
         * Verifies that filtering by category delegates to the repository correctly
         * and returns the matching products.
         */
        @Test
        @DisplayName("returns products matching the given category")
        void returnsProductsMatchingCategory() {
            when(productRepository.findByCategory("ELECTRONICS"))
                    .thenReturn(List.of(sampleProduct));

            List<Product> result = productService.getProductsByCategory("ELECTRONICS");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo("ELECTRONICS");
        }

        /**
         * Verifies that an unknown category returns an empty list.
         */
        @Test
        @DisplayName("returns empty list for unknown category")
        void returnsEmptyListForUnknownCategory() {
            when(productRepository.findByCategory("UNKNOWN")).thenReturn(List.of());

            List<Product> result = productService.getProductsByCategory("UNKNOWN");

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // createProduct()
    // =========================================================================

    @Nested
    @DisplayName("createProduct()")
    class CreateProductTests {

        /**
         * Verifies that createProduct() correctly maps the CreateProductRequest DTO
         * to a Product domain object and delegates to the repository.
         *
         * <p>We verify that the saved Product has the same field values as the request.
         */
        @Test
        @DisplayName("maps CreateProductRequest to Product and saves it")
        void mapsRequestAndSavesProduct() {
            // Arrange: prepare a create request
            CreateProductRequest request = new CreateProductRequest();
            request.setName("Smartwatch");
            request.setDescription("Fitness smartwatch with GPS");
            request.setPrice(new BigDecimal("199.99"));
            request.setCategory("ELECTRONICS");
            request.setStock(50);

            // The repository returns the product with an assigned ID
            Product savedProduct = new Product(
                    2L, "Smartwatch", "Fitness smartwatch with GPS",
                    new BigDecimal("199.99"), "ELECTRONICS", 50,
                    Instant.now(), Instant.now()
            );
            when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

            // Act
            Product result = productService.createProduct(request);

            // Assert: the result has the values from the request
            assertThat(result.getId()).isEqualTo(2L);
            assertThat(result.getName()).isEqualTo("Smartwatch");
            assertThat(result.getPrice()).isEqualByComparingTo("199.99");
            assertThat(result.getCategory()).isEqualTo("ELECTRONICS");

            // Verify the repository was called with a Product
            verify(productRepository).save(any(Product.class));
        }

        /**
         * Verifies that the description field is included in the mapped product.
         */
        @Test
        @DisplayName("includes optional description field in mapped product")
        void includesDescriptionInMappedProduct() {
            CreateProductRequest request = new CreateProductRequest();
            request.setName("Keyboard");
            request.setDescription("Mechanical gaming keyboard");
            request.setPrice(new BigDecimal("89.99"));
            request.setCategory("ELECTRONICS");
            request.setStock(30);

            Product savedProduct = new Product(
                    3L, "Keyboard", "Mechanical gaming keyboard",
                    new BigDecimal("89.99"), "ELECTRONICS", 30,
                    Instant.now(), Instant.now()
            );
            when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

            Product result = productService.createProduct(request);

            assertThat(result.getDescription()).isEqualTo("Mechanical gaming keyboard");
        }
    }

    // =========================================================================
    // updateProduct()
    // =========================================================================

    @Nested
    @DisplayName("updateProduct()")
    class UpdateProductTests {

        /**
         * Verifies that updateProduct() applies only non-null fields from the request.
         *
         * <p>When only the price is provided in the update request, only the price
         * should change; name, description, category, and stock remain as before.
         */
        @Test
        @DisplayName("applies only non-null fields (partial update)")
        void appliesOnlyNonNullFields() {
            // Arrange: existing product in repository
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(productRepository.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // Update request with only the price changed
            UpdateProductRequest request = new UpdateProductRequest();
            request.setPrice(new BigDecimal("129.99"));
            // name, description, category, stock are all null → should not change

            // Act
            Optional<Product> result = productService.updateProduct(1L, request);

            // Assert: price was updated, name remains unchanged
            assertThat(result).isPresent();
            assertThat(result.get().getPrice()).isEqualByComparingTo("129.99");
            assertThat(result.get().getName()).isEqualTo("Wireless Headphones"); // unchanged
            assertThat(result.get().getCategory()).isEqualTo("ELECTRONICS"); // unchanged
        }

        /**
         * Verifies that updateProduct() returns Optional.empty when the product
         * does not exist in the repository.
         */
        @Test
        @DisplayName("returns Optional.empty when product does not exist")
        void returnsEmptyWhenProductNotFound() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            UpdateProductRequest request = new UpdateProductRequest();
            request.setName("Updated Name");

            Optional<Product> result = productService.updateProduct(99L, request);

            assertThat(result).isEmpty();
        }

        /**
         * Verifies that all non-null update fields are applied when a full update is provided.
         */
        @Test
        @DisplayName("applies all non-null fields when full update is provided")
        void appliesAllNonNullFields() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(productRepository.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProductRequest request = new UpdateProductRequest();
            request.setName("Premium Headphones");
            request.setDescription("Updated description");
            request.setPrice(new BigDecimal("199.99"));
            request.setCategory("AUDIO");
            request.setStock(100);

            Optional<Product> result = productService.updateProduct(1L, request);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Premium Headphones");
            assertThat(result.get().getDescription()).isEqualTo("Updated description");
            assertThat(result.get().getPrice()).isEqualByComparingTo("199.99");
            assertThat(result.get().getCategory()).isEqualTo("AUDIO");
            assertThat(result.get().getStock()).isEqualTo(100);
        }
    }

    // =========================================================================
    // deleteProduct()
    // =========================================================================

    @Nested
    @DisplayName("deleteProduct()")
    class DeleteProductTests {

        /**
         * Verifies that deleteProduct() returns true when the product was deleted.
         */
        @Test
        @DisplayName("returns true when product is deleted successfully")
        void returnsTrueWhenDeleted() {
            when(productRepository.deleteById(1L)).thenReturn(true);

            boolean result = productService.deleteProduct(1L);

            assertThat(result).isTrue();
        }

        /**
         * Verifies that deleteProduct() returns false when no product with the
         * given ID exists.
         */
        @Test
        @DisplayName("returns false when product does not exist")
        void returnsFalseWhenNotFound() {
            when(productRepository.deleteById(99L)).thenReturn(false);

            boolean result = productService.deleteProduct(99L);

            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // getProductCount()
    // =========================================================================

    @Test
    @DisplayName("getProductCount() delegates to repository.count()")
    void getProductCountDelegatesToRepository() {
        when(productRepository.count()).thenReturn(6);

        int count = productService.getProductCount();

        assertThat(count).isEqualTo(6);
    }
}
