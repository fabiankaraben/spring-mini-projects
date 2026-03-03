package com.example.queryparser;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductSearchController.class)
public class ProductSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // --- Tests for POJO Validation (/api/products/search-pojo) ---

    @Test
    void searchWithPojo_ValidParameters_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/products/search-pojo")
                .param("q", "laptop")
                .param("minPrice", "100")
                .param("maxPrice", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("laptop"))
                .andExpect(jsonPath("$.minPrice").value(100))
                .andExpect(jsonPath("$.maxPrice").value(2000));
    }

    @Test
    void searchWithPojo_MissingQuery_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/search-pojo")
                .param("minPrice", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.q").value("Query cannot be blank"));
    }

    @Test
    void searchWithPojo_QueryTooShort_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/search-pojo")
                .param("q", "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.q").value("Query must be between 3 and 50 characters"));
    }

    @Test
    void searchWithPojo_NegativePrice_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/search-pojo")
                .param("q", "laptop")
                .param("minPrice", "-50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.minPrice").value("Minimum price cannot be negative"));
    }

    // --- Tests for @RequestParam Validation (/api/products/search-params) ---

    @Test
    void searchWithParams_ValidParameters_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/products/search-params")
                .param("category", "electronics")
                .param("limit", "20")
                .param("tags", "new", "sale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("electronics"))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.tags[0]").value("new"))
                .andExpect(jsonPath("$.tags[1]").value("sale"));
    }

    @Test
    void searchWithParams_MissingRequiredCategory_ReturnsBadRequest() throws Exception {
        // Here, the missing category parameter causes Spring to throw
        // MissingServletRequestParameterException
        mockMvc.perform(get("/api/products/search-params")
                .param("limit", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchWithParams_CategoryBlank_ReturnsBadRequest() throws Exception {
        // Blank category param ("") will trigger the @NotBlank validation
        mockMvc.perform(get("/api/products/search-params")
                .param("category", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.category").value("Category is required"));
    }

    @Test
    void searchWithParams_LimitBelowMinimum_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/search-params")
                .param("category", "electronics")
                .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.limit").value("Limit must be at least 1"));
    }
}
