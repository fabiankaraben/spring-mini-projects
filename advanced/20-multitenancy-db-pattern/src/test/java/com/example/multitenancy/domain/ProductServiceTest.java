package com.example.multitenancy.domain;

import com.example.multitenancy.web.dto.ProductRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>These tests use Mockito to mock the {@link ProductRepository} so that the
 * service logic is tested in complete isolation — no Spring context, no database.
 * The focus is on business logic: method delegation, return value mapping, and
 * the delete-existence-check pattern.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService — business logic unit tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;
    private ProductRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product("Laptop Pro", "High-end laptop", new BigDecimal("1299.99"), 10);
        sampleRequest = new ProductRequest("Laptop Pro", "High-end laptop", new BigDecimal("1299.99"), 10);
    }

    // -------------------------------------------------------------------------
    // findAll()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAll() should return all products from repository")
    void findAllShouldReturnAllProducts() {
        Product anotherProduct = new Product("Mouse", "Wireless mouse", new BigDecimal("29.99"), 50);
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct, anotherProduct));

        List<Product> result = productService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(sampleProduct, anotherProduct);
        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("findAll() should return empty list when no products exist")
    void findAllShouldReturnEmptyListWhenNoProducts() {
        when(productRepository.findAll()).thenReturn(List.of());

        List<Product> result = productService.findAll();

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // search()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("search() should delegate to repository with the given keyword")
    void searchShouldDelegateToRepository() {
        when(productRepository.findByNameContainingIgnoreCase("laptop"))
                .thenReturn(List.of(sampleProduct));

        List<Product> result = productService.search("laptop");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Laptop Pro");
        verify(productRepository).findByNameContainingIgnoreCase("laptop");
    }

    @Test
    @DisplayName("search() should return empty list when no products match")
    void searchShouldReturnEmptyListWhenNoMatch() {
        when(productRepository.findByNameContainingIgnoreCase("xyz"))
                .thenReturn(List.of());

        List<Product> result = productService.search("xyz");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findById()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findById() should return product when found")
    void findByIdShouldReturnProductWhenFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        Optional<Product> result = productService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Laptop Pro");
    }

    @Test
    @DisplayName("findById() should return empty Optional when product not found")
    void findByIdShouldReturnEmptyWhenNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Product> result = productService.findById(99L);

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // create()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("create() should map request to entity and delegate to repository.save()")
    void createShouldMapRequestAndSave() {
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        Product result = productService.create(sampleRequest);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Laptop Pro");
        assertThat(result.getPrice()).isEqualByComparingTo("1299.99");
        // Verify save() was called exactly once
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("create() should pass all fields from request to the new product")
    void createShouldPassAllFieldsFromRequest() {
        // Capture the product passed to save()
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest(
                "Mechanical Keyboard", "RGB backlit", new BigDecimal("89.99"), 25);
        Product result = productService.create(request);

        assertThat(result.getName()).isEqualTo("Mechanical Keyboard");
        assertThat(result.getDescription()).isEqualTo("RGB backlit");
        assertThat(result.getPrice()).isEqualByComparingTo("89.99");
        assertThat(result.getStockQuantity()).isEqualTo(25);
    }

    // -------------------------------------------------------------------------
    // update()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("update() should update and return the product when found")
    void updateShouldReturnUpdatedProductWhenFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductRequest updateRequest = new ProductRequest(
                "Laptop Pro Max", "Updated laptop", new BigDecimal("1499.99"), 5);
        Optional<Product> result = productService.update(1L, updateRequest);

        assertThat(result).isPresent();
        // The entity was mutated in-place by the service
        assertThat(sampleProduct.getName()).isEqualTo("Laptop Pro Max");
        assertThat(sampleProduct.getPrice()).isEqualByComparingTo("1499.99");
        verify(productRepository).save(sampleProduct);
    }

    @Test
    @DisplayName("update() should return empty Optional when product not found")
    void updateShouldReturnEmptyWhenNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Product> result = productService.update(99L, sampleRequest);

        assertThat(result).isEmpty();
        // save() must NOT be called if the product was not found
        verify(productRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("delete() should return true and delete the product when it exists")
    void deleteShouldReturnTrueWhenProductExists() {
        when(productRepository.existsById(1L)).thenReturn(true);

        boolean result = productService.delete(1L);

        assertThat(result).isTrue();
        verify(productRepository).deleteById(1L);
    }

    @Test
    @DisplayName("delete() should return false and not call deleteById() when product not found")
    void deleteShouldReturnFalseWhenProductNotFound() {
        when(productRepository.existsById(99L)).thenReturn(false);

        boolean result = productService.delete(99L);

        assertThat(result).isFalse();
        verify(productRepository, never()).deleteById(any());
    }
}
