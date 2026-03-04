package com.example.error.controller;

import com.example.error.exception.InvalidProductException;
import com.example.error.exception.ProductNotFoundException;
import com.example.error.model.Product;
import com.example.error.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced integration tests for ProductController and GlobalExceptionHandler.
 * We use @WebMvcTest which loads only the web layer and isolates it by mocking
 * the service.
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Use MockitoBean in Spring Boot 3.4+
    @MockitoBean
    private ProductService productService;

    @Test
    void testGetProduct_Success() throws Exception {
        // Arrange
        Product mockedProduct = new Product(1L, "Mocked Product", 100.0);
        when(productService.getProduct(1L)).thenReturn(mockedProduct);

        // Act & Assert
        mockMvc.perform(get("/api/products/1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Mocked Product"))
                .andExpect(jsonPath("$.price").value(100.0));
    }

    @Test
    void testGetProduct_NotFound() throws Exception {
        // Arrange
        when(productService.getProduct(2L)).thenThrow(new ProductNotFoundException(2L));

        // Act & Assert
        // ID 2 should trigger ProductNotFoundException which is mapped to 404
        mockMvc.perform(get("/api/products/2")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                // Verify the response is an RFC 7807 Problem Detail payload
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://example.com/problems/product-not-found"))
                .andExpect(jsonPath("$.title").value("Product Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Product with ID 2 not found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.instance").exists());
    }

    @Test
    @SuppressWarnings("null")
    void testCreateProduct_Success() throws Exception {
        // Arrange
        Product requestProduct = new Product(null, "New Product", 50.0);
        Product savedProduct = new Product(2L, "New Product", 50.0);
        when(productService.createProduct(any(Product.class))).thenReturn(savedProduct);

        // Act & Assert
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestProduct)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("New Product"))
                .andExpect(jsonPath("$.price").value(50.0));
    }

    @Test
    @SuppressWarnings("null")
    void testCreateProduct_Invalid() throws Exception {
        // Arrange
        Product invalidProduct = new Product(null, "Invalid Product", -10.0);
        when(productService.createProduct(any(Product.class)))
                .thenThrow(new InvalidProductException("Price cannot be negative"));

        // Act & Assert
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidProduct)))
                .andExpect(status().isBadRequest())
                // Verify the response is an RFC 7807 Problem Detail payload
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://example.com/problems/invalid-product"))
                .andExpect(jsonPath("$.title").value("Invalid Product Details"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Price cannot be negative"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.instance").exists());
    }
}
