package com.example.error.service;

import com.example.error.exception.InvalidProductException;
import com.example.error.exception.ProductNotFoundException;
import com.example.error.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for ProductService.
 * This class ensures our business logic operates independently of
 * web layer components.
 */
class ProductServiceTest {

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService();
    }

    @Test
    void testGetProduct_Success() {
        // Arrange
        Long targetId = 1L;

        // Act
        Product product = productService.getProduct(targetId);

        // Assert
        assertNotNull(product);
        assertEquals(targetId, product.id());
        assertEquals("Sample Product", product.name());
        assertEquals(99.99, product.price());
    }

    @Test
    void testGetProduct_NotFound() {
        // Arrange
        Long invalidId = 2L;

        // Act & Assert
        ProductNotFoundException exception = assertThrows(ProductNotFoundException.class, () -> {
            productService.getProduct(invalidId);
        });

        assertEquals("Product with ID 2 not found", exception.getMessage());
    }

    @Test
    void testCreateProduct_Success() {
        // Arrange
        Product newProduct = new Product(null, "Test Product", 50.5);

        // Act
        Product savedProduct = productService.createProduct(newProduct);

        // Assert
        assertNotNull(savedProduct.id());
        assertEquals("Test Product", savedProduct.name());
        assertEquals(50.5, savedProduct.price());
    }

    @Test
    void testCreateProduct_InvalidPrice() {
        // Arrange
        Product invalidProduct = new Product(null, "Bad Product", -10.0);

        // Act & Assert
        InvalidProductException exception = assertThrows(InvalidProductException.class, () -> {
            productService.createProduct(invalidProduct);
        });

        assertEquals("Price cannot be negative", exception.getMessage());
    }
}
