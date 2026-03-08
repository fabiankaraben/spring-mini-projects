package com.example.banner;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the BannerController.
 * <p>
 * This test uses @WebMvcTest, which is a specialized annotation for testing Spring MVC controllers.
 * It slices the application context to only load beans relevant to the web layer (like controllers,
 * filters, and web mvc config), making the test faster than a full @SpringBootTest.
 * </p>
 */
@WebMvcTest(BannerController.class)
class BannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BannerService bannerService;

    /**
     * Tests the root endpoint ("/") to ensure it returns the expected welcome message.
     * <p>
     * uses MockMvc to perform a GET request and asserts that:
     * 1. The HTTP status is 200 OK.
     * 2. The response body matches the expected string.
     * </p>
     *
     * @throws Exception if the request fails
     */
    @Test
    void shouldReturnWelcomeMessage() throws Exception {
        String expectedMessage = "Application started successfully! Check the console for the custom banner.";
        when(bannerService.getWelcomeMessage()).thenReturn(expectedMessage);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedMessage));
    }
}
