package com.example.queryparser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ProductSearchControllerUnitTest {

    @InjectMocks
    private ProductSearchController controller;

    @Test
    void searchWithPojo_ReturnsExpectedMap() {
        // Given
        ProductSearchController.ProductSearchCriteria criteria = new ProductSearchController.ProductSearchCriteria(
                "tablet", 100, 500);

        // When
        Map<String, Object> result = controller.searchWithPojo(criteria);

        // Then
        assertNotNull(result);
        assertEquals("tablet", result.get("query"));
        assertEquals(100, result.get("minPrice"));
        assertEquals(500, result.get("maxPrice"));
        assertEquals("Search executed successfully via POJO mapping", result.get("message"));
    }

    @Test
    void searchWithParams_ReturnsExpectedMap() {
        // Given
        String category = "shoes";
        int limit = 5;
        List<String> tags = List.of("discount", "summer");

        // When
        Map<String, Object> result = controller.searchWithParams(category, limit, tags);

        // Then
        assertNotNull(result);
        assertEquals("shoes", result.get("category"));
        assertEquals(5, result.get("limit"));
        assertEquals(tags, result.get("tags"));
        assertEquals("Search executed successfully via individual @RequestParam", result.get("message"));
    }
}
