package com.example.mongodbcrudapi.service;

import com.example.mongodbcrudapi.domain.Product;
import com.example.mongodbcrudapi.dto.ProductRequest;
import com.example.mongodbcrudapi.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>These tests exercise the service's business logic in pure isolation:
 * <ul>
 *   <li>The {@link ProductRepository} is replaced with a Mockito mock, so no real
 *       MongoDB connection is needed. Tests run in milliseconds without Docker.</li>
 *   <li>No Spring context is loaded — {@link ExtendWith}({@link MockitoExtension}.class)
 *       initialises Mockito annotations only, keeping startup time near zero.</li>
 *   <li>Each test follows the Given / When / Then (Arrange / Act / Assert) pattern
 *       to make intent and expectations explicit for educational clarity.</li>
 * </ul>
 *
 * <p>Integration tests (see {@link com.example.mongodbcrudapi.ProductIntegrationTest})
 * cover the full stack with a real MongoDB container via Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService unit tests")
class ProductServiceTest {

    /**
     * Mockito mock of the repository – no real MongoDB is involved.
     * All interactions with MongoDB are simulated via {@code when(...).thenReturn(...)}.
     */
    @Mock
    private ProductRepository productRepository;

    /**
     * Mockito mock of {@link MongoTemplate}.
     * Required because {@link ProductService} now accepts a {@code MongoTemplate}
     * in its constructor (used for the price-filter query).
     * {@code @InjectMocks} will inject this mock alongside the repository mock.
     */
    @Mock
    private MongoTemplate mongoTemplate;

    /**
     * The class under test.
     * {@code @InjectMocks} asks Mockito to create a {@link ProductService} instance
     * and inject the {@code @Mock} fields into it via constructor injection.
     */
    @InjectMocks
    private ProductService productService;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    /** A pre-built product returned by the mock repository. */
    private Product sampleProduct;

    /** A DTO that would arrive in an HTTP POST/PUT request body. */
    private ProductRequest sampleRequest;

    @BeforeEach
    void setUp() {
        // Build a sample domain object that the mock repository will return
        sampleProduct = new Product(
                "Laptop Pro",
                "High-performance laptop for developers",
                new BigDecimal("1299.99"),
                "electronics",
                50
        );
        sampleProduct.setId("507f1f77bcf86cd799439011");

        // Build a corresponding request DTO
        sampleRequest = new ProductRequest(
                "Laptop Pro",
                "High-performance laptop for developers",
                new BigDecimal("1299.99"),
                "electronics",
                50
        );
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns all products from the repository")
    void findAll_returnsAllProducts() {
        // Given: the repository holds two products
        Product second = new Product("Mouse", "Wireless mouse", new BigDecimal("29.99"), "electronics", 200);
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct, second));

        // When
        List<Product> results = productService.findAll();

        // Then: both products are returned and the repository was called exactly once
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Product::getName)
                .containsExactlyInAnyOrder("Laptop Pro", "Mouse");
        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll returns empty list when the collection is empty")
    void findAll_returnsEmptyList_whenCollectionIsEmpty() {
        // Given: the repository has no documents
        when(productRepository.findAll()).thenReturn(List.of());

        // When
        List<Product> results = productService.findAll();

        // Then: an empty list is returned (never null)
        assertThat(results).isNotNull().isEmpty();
        verify(productRepository, times(1)).findAll();
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns the product wrapped in Optional when it exists")
    void findById_returnsProduct_whenExists() {
        // Given: the repository finds a document for the given ID
        when(productRepository.findById("507f1f77bcf86cd799439011"))
                .thenReturn(Optional.of(sampleProduct));

        // When
        Optional<Product> result = productService.findById("507f1f77bcf86cd799439011");

        // Then: the product is present with the correct data
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("507f1f77bcf86cd799439011");
        assertThat(result.get().getName()).isEqualTo("Laptop Pro");
        verify(productRepository, times(1)).findById("507f1f77bcf86cd799439011");
    }

    @Test
    @DisplayName("findById returns empty Optional when the product does not exist")
    void findById_returnsEmpty_whenNotFound() {
        // Given: the repository returns nothing for an unknown ID
        when(productRepository.findById("nonexistent-id")).thenReturn(Optional.empty());

        // When
        Optional<Product> result = productService.findById("nonexistent-id");

        // Then: the Optional is empty and the repository was still consulted
        assertThat(result).isEmpty();
        verify(productRepository, times(1)).findById("nonexistent-id");
    }

    // ── findByCategory ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByCategory returns products in the given category")
    void findByCategory_returnsMatchingProducts() {
        // Given: two electronics products
        Product second = new Product("Keyboard", "Mechanical keyboard", new BigDecimal("79.99"), "electronics", 100);
        when(productRepository.findByCategory("electronics"))
                .thenReturn(List.of(sampleProduct, second));

        // When
        List<Product> results = productService.findByCategory("electronics");

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(p -> "electronics".equals(p.getCategory()));
        verify(productRepository, times(1)).findByCategory("electronics");
    }

    @Test
    @DisplayName("findByCategory returns empty list when no products match the category")
    void findByCategory_returnsEmpty_whenNoMatch() {
        // Given: no products in the "garden" category
        when(productRepository.findByCategory("garden")).thenReturn(List.of());

        // When
        List<Product> results = productService.findByCategory("garden");

        // Then
        assertThat(results).isEmpty();
    }

    // ── findByMaxPrice ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByMaxPrice returns products priced at or below the limit")
    void findByMaxPrice_returnsProductsWithinBudget() {
        // Given: a cheap product in the price range
        Product cheapProduct = new Product("USB Cable", "1m USB-C cable", new BigDecimal("9.99"), "accessories", 500);
        // The service uses MongoTemplate.find(Query, Class) — stub that call
        when(mongoTemplate.find(any(Query.class), eq(Product.class)))
                .thenReturn(List.of(cheapProduct));

        // When
        List<Product> results = productService.findByMaxPrice(new BigDecimal("50.00"));

        // Then: only the product within budget is returned
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("USB Cable");
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(Product.class));
    }

    // ── searchByName ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("searchByName returns products whose names contain the search term")
    void searchByName_returnsMatchingProducts() {
        // Given: a product whose name contains "laptop" (case-insensitively)
        when(productRepository.findByNameContainingIgnoreCase("laptop"))
                .thenReturn(List.of(sampleProduct));

        // When
        List<Product> results = productService.searchByName("laptop");

        // Then: the matching product is returned
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualToIgnoringCase("Laptop Pro");
        verify(productRepository, times(1)).findByNameContainingIgnoreCase("laptop");
    }

    // ── findLowStockProducts ──────────────────────────────────────────────────────

    @Test
    @DisplayName("findLowStockProducts returns products with stock below threshold")
    void findLowStockProducts_returnsLowStockItems() {
        // Given: a product with only 3 units left
        Product lowStock = new Product("Rare Item", "Almost sold out", new BigDecimal("499.99"), "collectibles", 3);
        when(productRepository.findLowStockProducts(10)).thenReturn(List.of(lowStock));

        // When
        List<Product> results = productService.findLowStockProducts(10);

        // Then: the low-stock product is returned
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStockQuantity()).isLessThan(10);
        verify(productRepository, times(1)).findLowStockProducts(10);
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create maps the request DTO to a Product and persists it")
    void create_persistsProductAndReturnsWithGeneratedId() {
        // Given: the repository assigns a MongoDB ObjectId when saving
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId("507f1f77bcf86cd799439011"); // simulate MongoDB-generated ObjectId
            return p;
        });

        // When
        Product created = productService.create(sampleRequest);

        // Then: the created product has an assigned ID and the correct data
        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo("507f1f77bcf86cd799439011");
        assertThat(created.getName()).isEqualTo(sampleRequest.getName());
        assertThat(created.getDescription()).isEqualTo(sampleRequest.getDescription());
        assertThat(created.getPrice()).isEqualByComparingTo(sampleRequest.getPrice());
        assertThat(created.getCategory()).isEqualTo(sampleRequest.getCategory());
        assertThat(created.getStockQuantity()).isEqualTo(sampleRequest.getStockQuantity());
        // Timestamps should have been set in the constructor
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();

        verify(productRepository, times(1)).save(any(Product.class));
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update returns updated product wrapped in Optional when the product exists")
    void update_returnsUpdatedProduct_whenExists() {
        // Given: the existing product is found by ID
        when(productRepository.findById("507f1f77bcf86cd799439011"))
                .thenReturn(Optional.of(sampleProduct));
        // And save returns the mutated product
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // New values to apply
        ProductRequest updateRequest = new ProductRequest(
                "Laptop Ultra",
                "Even more powerful laptop",
                new BigDecimal("1999.99"),
                "electronics",
                25
        );

        // When
        Optional<Product> result = productService.update("507f1f77bcf86cd799439011", updateRequest);

        // Then: the updated product is returned with the new values
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Laptop Ultra");
        assertThat(result.get().getDescription()).isEqualTo("Even more powerful laptop");
        assertThat(result.get().getPrice()).isEqualByComparingTo("1999.99");
        assertThat(result.get().getStockQuantity()).isEqualTo(25);
        // updatedAt should have been refreshed
        assertThat(result.get().getUpdatedAt()).isNotNull();

        verify(productRepository, times(1)).findById("507f1f77bcf86cd799439011");
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("update returns empty Optional when the product does not exist")
    void update_returnsEmpty_whenProductNotFound() {
        // Given: no product with the given ID
        when(productRepository.findById("nonexistent-id")).thenReturn(Optional.empty());

        // When
        Optional<Product> result = productService.update("nonexistent-id", sampleRequest);

        // Then: an empty Optional is returned and save is never called
        assertThat(result).isEmpty();
        verify(productRepository, times(1)).findById("nonexistent-id");
        verify(productRepository, never()).save(any(Product.class));
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById returns true when the product exists and is deleted")
    void deleteById_returnsTrue_whenProductExists() {
        // Given: the product exists
        when(productRepository.existsById("507f1f77bcf86cd799439011")).thenReturn(true);

        // When
        boolean result = productService.deleteById("507f1f77bcf86cd799439011");

        // Then: true is returned and deleteById was called on the repository
        assertThat(result).isTrue();
        verify(productRepository, times(1)).existsById("507f1f77bcf86cd799439011");
        verify(productRepository, times(1)).deleteById("507f1f77bcf86cd799439011");
    }

    @Test
    @DisplayName("deleteById returns false when the product does not exist")
    void deleteById_returnsFalse_whenProductNotFound() {
        // Given: no product with the given ID
        when(productRepository.existsById("nonexistent-id")).thenReturn(false);

        // When
        boolean result = productService.deleteById("nonexistent-id");

        // Then: false is returned and deleteById was never called (nothing to delete)
        assertThat(result).isFalse();
        verify(productRepository, times(1)).existsById("nonexistent-id");
        verify(productRepository, never()).deleteById(anyString());
    }
}
