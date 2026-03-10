package com.example.cassandraintegration.service;

import com.example.cassandraintegration.domain.Product;
import com.example.cassandraintegration.dto.CreateProductRequest;
import com.example.cassandraintegration.dto.UpdateProductRequest;
import com.example.cassandraintegration.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>Uses Mockito to replace the real {@link ProductRepository} with a mock,
 * allowing tests to run instantly without any Cassandra connection or
 * Spring application context.</p>
 *
 * <p>The {@code @ExtendWith(MockitoExtension.class)} annotation activates
 * Mockito's JUnit 5 extension, which initialises {@code @Mock} and
 * {@code @InjectMocks} fields before each test method.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService unit tests")
class ProductServiceTest {

    /** Mock repository — no real Cassandra connection. */
    @Mock
    private ProductRepository productRepository;

    /** The real service under test, with the mock repository injected. */
    @InjectMocks
    private ProductService productService;

    /** Shared test fixtures. */
    private static final String CATEGORY = "Electronics";
    private UUID laptopId;
    private UUID mouseId;
    private Product laptop;
    private Product mouse;

    @BeforeEach
    void setUp() {
        laptopId = UUID.randomUUID();
        mouseId  = UUID.randomUUID();

        laptop = new Product(CATEGORY, laptopId, "Laptop Pro",
                "High-end laptop", new BigDecimal("1299.99"), 20);
        mouse  = new Product(CATEGORY, mouseId, "Wireless Mouse",
                "Ergonomic mouse", new BigDecimal("49.99"), 100);
    }

    // -------------------------------------------------------------------------
    // createProduct tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createProduct should generate a UUID and persist the product")
    void createProduct_generatesUuidAndPersists() {
        // Arrange: when repository.save() is called with any Product, return laptop
        when(productRepository.save(any(Product.class))).thenReturn(laptop);

        CreateProductRequest request = new CreateProductRequest(
                CATEGORY, "Laptop Pro", "High-end laptop",
                new BigDecimal("1299.99"), 20
        );

        // Act
        Product result = productService.createProduct(request);

        // Assert: returned product has the expected fields
        assertThat(result.getCategory()).isEqualTo(CATEGORY);
        assertThat(result.getName()).isEqualTo("Laptop Pro");
        assertThat(result.getPrice()).isEqualByComparingTo("1299.99");
        assertThat(result.getStock()).isEqualTo(20);

        // Verify: repository.save() was called exactly once with any Product argument
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct should map description correctly (including null)")
    void createProduct_mapsDescriptionCorrectly() {
        Product noDesc = new Product(CATEGORY, UUID.randomUUID(), "Gadget", null,
                new BigDecimal("9.99"), 5);
        when(productRepository.save(any(Product.class))).thenReturn(noDesc);

        CreateProductRequest request = new CreateProductRequest(
                CATEGORY, "Gadget", null, new BigDecimal("9.99"), 5
        );

        Product result = productService.createProduct(request);

        assertThat(result.getDescription()).isNull();
    }

    // -------------------------------------------------------------------------
    // findByCategory tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByCategory should return all products in the category")
    void findByCategory_returnsAllProducts() {
        when(productRepository.findByCategory(CATEGORY)).thenReturn(List.of(laptop, mouse));

        List<Product> result = productService.findByCategory(CATEGORY);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Product::getName)
                .containsExactlyInAnyOrder("Laptop Pro", "Wireless Mouse");
    }

    @Test
    @DisplayName("findByCategory should return empty list when category has no products")
    void findByCategory_returnsEmptyList() {
        when(productRepository.findByCategory("EmptyCategory")).thenReturn(List.of());

        assertThat(productService.findByCategory("EmptyCategory")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByCategoryAndId tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByCategoryAndId should return present Optional when found")
    void findByCategoryAndId_returnsProductWhenFound() {
        when(productRepository.findByCategoryAndId(CATEGORY, laptopId))
                .thenReturn(Optional.of(laptop));

        Optional<Product> result = productService.findByCategoryAndId(CATEGORY, laptopId);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Laptop Pro");
    }

    @Test
    @DisplayName("findByCategoryAndId should return empty Optional when not found")
    void findByCategoryAndId_returnsEmptyWhenNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findByCategoryAndId(CATEGORY, unknownId))
                .thenReturn(Optional.empty());

        Optional<Product> result = productService.findByCategoryAndId(CATEGORY, unknownId);

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByPriceAtMost tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByPriceAtMost should return products at or below maxPrice")
    void findByPriceAtMost_returnsFilteredProducts() {
        when(productRepository.findByCategoryAndPriceLessThanEqual(
                CATEGORY, new BigDecimal("100")))
                .thenReturn(List.of(mouse));

        List<Product> result = productService.findByPriceAtMost(CATEGORY, new BigDecimal("100"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Wireless Mouse");
    }

    @Test
    @DisplayName("findByPriceAtMost should return empty list when no products match")
    void findByPriceAtMost_returnsEmptyList() {
        when(productRepository.findByCategoryAndPriceLessThanEqual(
                CATEGORY, new BigDecimal("5")))
                .thenReturn(List.of());

        assertThat(productService.findByPriceAtMost(CATEGORY, new BigDecimal("5"))).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findInStock tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findInStock should return products with stock above threshold")
    void findInStock_returnsProductsAboveThreshold() {
        when(productRepository.findByCategoryAndStockGreaterThan(CATEGORY, 50))
                .thenReturn(List.of(mouse));

        List<Product> result = productService.findInStock(CATEGORY, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStock()).isGreaterThan(50);
    }

    @Test
    @DisplayName("findInStock should return empty list when no products meet threshold")
    void findInStock_returnsEmptyList() {
        when(productRepository.findByCategoryAndStockGreaterThan(CATEGORY, 500))
                .thenReturn(List.of());

        assertThat(productService.findInStock(CATEGORY, 500)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // updateProduct tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateProduct should apply all non-null fields from the request")
    void updateProduct_appliesAllNonNullFields() {
        when(productRepository.findByCategoryAndId(CATEGORY, laptopId))
                .thenReturn(Optional.of(laptop));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductRequest request = new UpdateProductRequest(
                "Laptop Pro Max", "Updated description",
                new BigDecimal("999.99"), 15
        );

        Product result = productService.updateProduct(CATEGORY, laptopId, request);

        assertThat(result.getName()).isEqualTo("Laptop Pro Max");
        assertThat(result.getDescription()).isEqualTo("Updated description");
        assertThat(result.getPrice()).isEqualByComparingTo("999.99");
        assertThat(result.getStock()).isEqualTo(15);

        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("updateProduct should only update non-null fields (partial update)")
    void updateProduct_partialUpdate_onlyUpdatesNonNullFields() {
        when(productRepository.findByCategoryAndId(CATEGORY, laptopId))
                .thenReturn(Optional.of(laptop));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Only update stock; other fields are null → keep existing values
        UpdateProductRequest request = new UpdateProductRequest(null, null, null, 5);

        Product result = productService.updateProduct(CATEGORY, laptopId, request);

        // Name and price should be unchanged from the original fixture
        assertThat(result.getName()).isEqualTo("Laptop Pro");
        assertThat(result.getPrice()).isEqualByComparingTo("1299.99");
        // Stock should be updated
        assertThat(result.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("updateProduct should throw IllegalArgumentException when product not found")
    void updateProduct_throwsWhenNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findByCategoryAndId(CATEGORY, unknownId))
                .thenReturn(Optional.empty());

        UpdateProductRequest request = new UpdateProductRequest("New Name", null, null, null);

        assertThatThrownBy(() -> productService.updateProduct(CATEGORY, unknownId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
    }

    // -------------------------------------------------------------------------
    // deleteProduct tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deleteProduct should call repository.delete with the found product")
    void deleteProduct_callsRepositoryDelete() {
        when(productRepository.findByCategoryAndId(CATEGORY, laptopId))
                .thenReturn(Optional.of(laptop));
        doNothing().when(productRepository).delete(laptop);

        productService.deleteProduct(CATEGORY, laptopId);

        verify(productRepository, times(1)).delete(laptop);
    }

    @Test
    @DisplayName("deleteProduct should throw IllegalArgumentException when product not found")
    void deleteProduct_throwsWhenNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findByCategoryAndId(CATEGORY, unknownId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(CATEGORY, unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
    }
}
