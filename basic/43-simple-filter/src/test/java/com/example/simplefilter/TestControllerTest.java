package com.example.simplefilter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced integration test for the TestController using @WebMvcTest.
 * This test focuses on the web layer and verifies the controller's behavior.
 * Note: Filters are not automatically included in @WebMvcTest; this test focuses on the controller response.
 */
@WebMvcTest(TestController.class)
class TestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Tests the /hello endpoint.
     * Verifies that the endpoint returns the expected response and status.
     */
    @Test
    void testHelloEndpoint() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, World! This request was processed by the LoggingFilter."));
    }
}
