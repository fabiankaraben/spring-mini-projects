package com.fabiankaraben.spring.miniprojects.datarest.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Product entity.
 */
class ProductTest {

    @Test
    void testProductCreation() {
        Product product = new Product("Test Product", "Description", 10.0);
        
        assertEquals("Test Product", product.getName());
        assertEquals("Description", product.getDescription());
        assertEquals(10.0, product.getPrice());
    }

    @Test
    void testSetters() {
        Product product = new Product();
        product.setName("New Name");
        product.setPrice(20.0);
        
        assertEquals("New Name", product.getName());
        assertEquals(20.0, product.getPrice());
    }
}
