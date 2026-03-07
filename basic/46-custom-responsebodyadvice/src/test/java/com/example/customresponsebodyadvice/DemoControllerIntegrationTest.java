package com.example.customresponsebodyadvice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the DemoController using @WebMvcTest.
 * This sliced test focuses on the web layer, including controllers and @ControllerAdvice.
 * Tests that the CustomResponseBodyAdvice is applied to responses.
 */
@WebMvcTest(DemoController.class)
class DemoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Test the /api/demo/data endpoint.
     * Verifies that the response contains the original data and the added processedAt field.
     */
    @Test
    void getDemoData_shouldReturnResponseWithProcessedAt() throws Exception {
        mockMvc.perform(get("/api/demo/data"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.message").value("This is a demo response from the Custom ResponseBodyAdvice mini-project"))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.key1").value("value1"))
                .andExpect(jsonPath("$.data.key2").value(42))
                .andExpect(jsonPath("$.processedAt").exists());
    }

    /**
     * Test the /api/demo/user endpoint.
     * Verifies that the response contains user data and the added processedAt field.
     */
    @Test
    void getUserInfo_shouldReturnResponseWithProcessedAt() throws Exception {
        mockMvc.perform(get("/api/demo/user"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.userId").value(12345))
                .andExpect(jsonPath("$.username").value("demoUser"))
                .andExpect(jsonPath("$.email").value("demo@example.com"))
                .andExpect(jsonPath("$.processedAt").exists());
    }
}
