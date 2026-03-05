package com.example.h2_database_setup.controller;

import com.example.h2_database_setup.model.Product;
import com.example.h2_database_setup.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void testGetAllProducts() throws Exception {
        Product p = new Product("Test", 100.0);
        when(productService.getAllProducts()).thenReturn(List.of(p));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test"));
    }

    @Test
    void testCreateProduct() throws Exception {
        Product p = new Product("Test", 100.0);
        when(productService.saveProduct(any(Product.class))).thenReturn(p);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\", \"price\":100.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test"));
    }
}
