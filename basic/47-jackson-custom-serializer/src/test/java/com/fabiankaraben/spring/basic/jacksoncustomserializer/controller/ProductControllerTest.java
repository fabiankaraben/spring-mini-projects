package com.fabiankaraben.spring.basic.jacksoncustomserializer.controller;

import com.fabiankaraben.spring.basic.jacksoncustomserializer.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSerializeProductPrice() throws Exception {
        mockMvc.perform(get("/products/example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Premium Headphones"))
                .andExpect(jsonPath("$.priceInCents").value("$299.99")) // Serialized value
                .andExpect(jsonPath("$.category").value("Electronics"));
    }

    @Test
    void shouldDeserializeProductPrice() throws Exception {
        // We send a JSON with formatted price
        String jsonContent = """
                {
                    "name": "Gaming Mouse",
                    "priceInCents": "$49.50",
                    "category": "Electronics"
                }
                """;

        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gaming Mouse"))
                .andExpect(jsonPath("$.priceInCents").value("$49.50")); // Should return serialized again
    }
}
