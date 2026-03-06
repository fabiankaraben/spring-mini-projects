package com.example.corsconfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for ApiController using @WebMvcTest.
 * This test slices the web layer and mocks the GreetingService using Mockito.
 * Demonstrates testing REST endpoints with CORS configuration applied.
 */
@WebMvcTest(ApiController.class)
public class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GreetingService greetingService;

    /**
     * Test the GET /api/hello endpoint.
     * Verifies that the controller calls the service and returns the mocked greeting.
     */
    @Test
    void testGetHello() throws Exception {
        // Given: Mock the service to return a specific greeting
        when(greetingService.getGreeting()).thenReturn("Mocked greeting");

        // When & Then: Perform GET request and verify the response
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Mocked greeting"));
    }

    /**
     * Test the POST /api/data endpoint.
     * Verifies that the endpoint accepts JSON data and echoes it back.
     */
    @Test
    void testCreateData() throws Exception {
        // Given: Sample data to send
        String testData = "test data";

        // When & Then: Perform POST request and verify the response
        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testData))
                .andExpect(status().isOk())
                .andExpect(content().string("Received data: " + testData));
    }

    /**
     * Test the PUT /api/data/{id} endpoint.
     * Verifies that the endpoint simulates updating a resource.
     */
    @Test
    void testUpdateData() throws Exception {
        // Given: Sample data and ID
        Long id = 1L;
        String updateData = "updated data";

        // When & Then: Perform PUT request and verify the response
        mockMvc.perform(put("/api/data/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateData))
                .andExpect(status().isOk())
                .andExpect(content().string("Updated resource with ID: " + id + " with data: " + updateData));
    }

    /**
     * Test the DELETE /api/data/{id} endpoint.
     * Verifies that the endpoint simulates deleting a resource.
     */
    @Test
    void testDeleteData() throws Exception {
        // Given: Sample ID
        Long id = 1L;

        // When & Then: Perform DELETE request and verify the response
        mockMvc.perform(delete("/api/data/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string("Deleted resource with ID: " + id));
    }
}
