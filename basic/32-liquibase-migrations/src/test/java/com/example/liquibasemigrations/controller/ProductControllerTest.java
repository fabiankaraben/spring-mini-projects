package com.example.liquibasemigrations.controller;

import com.example.liquibasemigrations.entity.Product;
import com.example.liquibasemigrations.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced integration test for {@link ProductController}.
 *
 * <p>
 * {@code @WebMvcTest} spins up only the Web MVC layer (controllers, filters,
 * ControllerAdvice) without starting a real HTTP server or loading the full
 * application context. This makes the test fast and focused on the HTTP
 * handling
 * logic rather than database connectivity.
 * </p>
 *
 * <p>
 * Because the JPA layer is excluded, {@link ProductRepository} is replaced with
 * a {@code @MockitoBean} so we can control what it returns without any
 * database.
 * </p>
 *
 * <p>
 * Note: {@code @MockitoBean} is used instead of the deprecated
 * {@code @MockBean}
 * ({@code org.springframework.boot.test.mock.mockito.MockBean}) as per project
 * rules.
 * </p>
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    /** MockMvc is the main tool for simulating HTTP requests in WebMvcTest. */
    @Autowired
    private MockMvc mockMvc;

    /**
     * We replace the real ProductRepository with a Mockito mock so the controller
     * test does not require any database. @MockitoBean registers the mock as a
     * Spring bean, replacing any existing bean of the same type in the context.
     */
    @MockitoBean
    private ProductRepository productRepository;

    /**
     * ObjectMapper is auto-configured by @WebMvcTest and used to serialize
     * Java objects into JSON for request bodies.
     */
    @Autowired
    private ObjectMapper objectMapper;

    // ---------------------------------------------------------
    // GET /api/products — return all products
    // ---------------------------------------------------------

    @Test
    void getAllProducts_returnsListOfProducts() throws Exception {
        // Arrange: the mock repository returns two products.
        // BigDecimal is used for price because it matches the DECIMAL(10,2) column
        // type created by Liquibase and the BigDecimal field in the Product entity.
        Product p1 = new Product("Wireless Headphones", new BigDecimal("79.99"), "Electronics");
        p1.setId(1L);
        Product p2 = new Product("Mechanical Keyboard", new BigDecimal("149.99"), "Electronics");
        p2.setId(2L);

        Mockito.when(productRepository.findAll()).thenReturn(List.of(p1, p2));

        // Act & Assert: perform the GET request and verify the response
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].name").value("Wireless Headphones"))
                .andExpect(jsonPath("$[1].name").value("Mechanical Keyboard"));
    }

    // ---------------------------------------------------------
    // GET /api/products/{id} — product found
    // ---------------------------------------------------------

    @Test
    void getProductById_whenFound_returns200() throws Exception {
        // Arrange
        Product product = new Product("USB-C Hub", new BigDecimal("39.99"), "Electronics");
        product.setId(3L);

        Mockito.when(productRepository.findById(3L)).thenReturn(Optional.of(product));

        // Act & Assert
        mockMvc.perform(get("/api/products/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("USB-C Hub"))
                .andExpect(jsonPath("$.price").value(39.99))
                .andExpect(jsonPath("$.category").value("Electronics"));
    }

    // ---------------------------------------------------------
    // GET /api/products/{id} — product NOT found -> 404
    // ---------------------------------------------------------

    @Test
    void getProductById_whenNotFound_returns404() throws Exception {
        // Arrange: repository returns empty Optional
        Mockito.when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert: controller should return 404
        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------
    // GET /api/products/category/{category}
    // ---------------------------------------------------------

    @Test
    void getProductsByCategory_returnsMatchingProducts() throws Exception {
        // Arrange
        Product p = new Product("Laptop Stand", new BigDecimal("29.99"), "Accessories");
        p.setId(4L);

        Mockito.when(productRepository.findByCategory("Accessories")).thenReturn(List.of(p));

        // Act & Assert
        mockMvc.perform(get("/api/products/category/Accessories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].category").value("Accessories"));
    }

    // ---------------------------------------------------------
    // POST /api/products — create a new product
    // ---------------------------------------------------------

    @Test
    void createProduct_returns201WithSavedProduct() throws Exception {
        // Arrange
        Product newProduct = new Product("Gaming Mouse", new BigDecimal("59.99"), "Gaming");
        Product savedProduct = new Product("Gaming Mouse", new BigDecimal("59.99"), "Gaming");
        savedProduct.setId(5L);

        Mockito.when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        // Act & Assert: POST body must be valid JSON; response must be 201 Created
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newProduct)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Gaming Mouse"));
    }

    // ---------------------------------------------------------
    // DELETE /api/products/{id} — successful deletion
    // ---------------------------------------------------------

    @Test
    void deleteProduct_whenFound_returns204() throws Exception {
        // Arrange: product exists
        Mockito.when(productRepository.existsById(1L)).thenReturn(true);

        // Act & Assert: 204 No Content on successful deletion
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        // Verify that deleteById was actually called once
        Mockito.verify(productRepository, Mockito.times(1)).deleteById(1L);
    }

    // ---------------------------------------------------------
    // DELETE /api/products/{id} — product does NOT exist -> 404
    // ---------------------------------------------------------

    @Test
    void deleteProduct_whenNotFound_returns404() throws Exception {
        // Arrange: product does not exist
        Mockito.when(productRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(delete("/api/products/999"))
                .andExpect(status().isNotFound());
    }
}
