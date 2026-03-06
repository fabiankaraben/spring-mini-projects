package com.example.mockmvctesting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class demonstrating @WebMvcTest for sliced integration testing.
 * This test loads only the web layer (controller) without the full Spring context,
 * making it faster and more focused on web-related functionality.
 */
@WebMvcTest(HelloController.class) // Slices the application context to only include web-related components
public class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc; // MockMvc is auto-configured by @WebMvcTest

    /**
     * Test the /hello endpoint.
     * Verifies that the endpoint returns "Hello, World!" with HTTP 200 status.
     */
    @Test
    public void testHello() throws Exception {
        mockMvc.perform(get("/hello")) // Perform GET request to /hello
                .andExpect(status().isOk()) // Expect HTTP 200 OK
                .andExpect(content().string("Hello, World!")); // Expect exact response body
    }

    /**
     * Test the /hello/{name} endpoint with a path variable.
     * Verifies that the endpoint returns a personalized greeting.
     */
    @Test
    public void testHelloName() throws Exception {
        mockMvc.perform(get("/hello/Alice")) // Perform GET request to /hello/Alice
                .andExpect(status().isOk()) // Expect HTTP 200 OK
                .andExpect(content().string("Hello, Alice!")); // Expect personalized response
    }
}
