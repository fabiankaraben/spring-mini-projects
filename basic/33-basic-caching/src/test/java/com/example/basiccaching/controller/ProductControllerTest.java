package com.example.basiccaching.controller;

import com.example.basiccaching.model.Product;
import com.example.basiccaching.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced integration tests for {@link ProductController} using
 * {@code @WebMvcTest}.
 *
 * <p>
 * {@code @WebMvcTest} spins up only the Web MVC layer (controllers, filters,
 * converters)
 * without loading the full Spring context. This makes these tests fast and
 * focused
 * exclusively on HTTP-level concerns (routing, request parsing, response
 * serialization,
 * status codes).
 * </p>
 *
 * <p>
 * The {@link ProductService} dependency is replaced with a Mockito mock via
 * {@code @MockitoBean} (the modern replacement for the deprecated
 * {@code @MockBean})
 * so the service layer does not execute — including its caching logic.
 * </p>
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // @MockitoBean replaces the deprecated @MockBean from spring-boot-test
    @MockitoBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper; // Spring's pre-configured Jackson serializer

    // ---------------------------------------------------------------------------
    // GET /api/products
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/products should return 200 with list of products")
    void testGetAllProducts() throws Exception {
        // Arrange: configure mock to return two products
        List<Product> products = List.of(
                new Product(1L, "Keyboard", "Electronics", 49.99),
                new Product(2L, "Mouse", "Electronics", 29.99));
        when(productService.findAll()).thenReturn(products);

        // Act & Assert
        mockMvc.perform(get("/api/products")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Keyboard"))
                .andExpect(jsonPath("$[1].name").value("Mouse"));
    }

    // ---------------------------------------------------------------------------
    // GET /api/products/{id}
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/products/{id} should return 200 when product exists")
    void testGetProductByIdFound() throws Exception {
        // Arrange
        Product product = new Product(1L, "Standing Desk", "Furniture", 349.00);
        when(productService.findById(1L)).thenReturn(Optional.of(product));

        // Act & Assert
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Standing Desk"))
                .andExpect(jsonPath("$.category").value("Furniture"))
                .andExpect(jsonPath("$.price").value(349.00));
    }

    @Test
    @DisplayName("GET /api/products/{id} should return 404 when product does not exist")
    void testGetProductByIdNotFound() throws Exception {
        // Arrange: service returns empty Optional
        when(productService.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------
    // POST /api/products
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/products should return 201 with created product")
    void testCreateProduct() throws Exception {
        // Arrange
        Product input = new Product(null, "Notebook", "Stationery", 4.99);
        Product saved = new Product(10L, "Notebook", "Stationery", 4.99);
        when(productService.save(any(Product.class))).thenReturn(saved);

        // Serialize the input to JSON
        String requestBody = objectMapper.writeValueAsString(input);

        // Act & Assert
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated()) // 201
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Notebook"));
    }

    // ---------------------------------------------------------------------------
    // PUT /api/products/{id}
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("PUT /api/products/{id} should return 200 with updated product when found")
    void testUpdateProductFound() throws Exception {
        // Arrange
        Product existing = new Product(1L, "Old Name", "Old Category", 10.0);
        Product updated = new Product(1L, "New Name", "New Category", 20.0);

        when(productService.findById(1L)).thenReturn(Optional.of(existing));
        when(productService.save(any(Product.class))).thenReturn(updated);

        String requestBody = objectMapper.writeValueAsString(updated);

        // Act & Assert
        mockMvc.perform(put("/api/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.price").value(20.0));
    }

    @Test
    @DisplayName("PUT /api/products/{id} should return 404 when product does not exist")
    void testUpdateProductNotFound() throws Exception {
        when(productService.findById(99L)).thenReturn(Optional.empty());

        Product body = new Product(99L, "Ghost", "None", 0.0);
        String requestBody = objectMapper.writeValueAsString(body);

        mockMvc.perform(put("/api/products/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/products/{id}
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/products/{id} should return 204 when product exists")
    void testDeleteProductFound() throws Exception {
        when(productService.deleteById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent()); // 204
    }

    @Test
    @DisplayName("DELETE /api/products/{id} should return 404 when product does not exist")
    void testDeleteProductNotFound() throws Exception {
        when(productService.deleteById(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/products/99"))
                .andExpect(status().isNotFound());
    }
}
