package com.example.simple_logger_middleware.controller;

import com.example.simple_logger_middleware.config.WebMvcConfig;
import com.example.simple_logger_middleware.interceptor.RequestLoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// In tests involving @WebMvcTest, some custom interceptors might not be automatically loaded
// unless their @Component classes are imported, or the WebMvcConfig is imported.
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sliced Integration Testing using @WebMvcTest.
 * This class tests the web layer without bringing up the complete entire
 * server.
 */
@WebMvcTest(HelloController.class)
@Import({ WebMvcConfig.class, RequestLoggingInterceptor.class }) // We need to import our interceptor and config
                                                                 // components since they are omitted by default in
                                                                 // slice slicing
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Testing if the endpoint actually successfully runs all the way through,
     * including our interceptor.
     * The response should be 200 OK and "Hello World!".
     * The log itself won't be asserted here, but this test verifies the interceptor
     * doesn't throw exceptions.
     */
    @Test
    void testHelloEndpoint() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World!"));
    }
}
