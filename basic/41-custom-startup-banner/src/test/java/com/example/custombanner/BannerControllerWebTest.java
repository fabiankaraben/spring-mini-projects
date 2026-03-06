package com.example.custombanner;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/**
 * Integration tests for BannerController using @WebMvcTest.
 * This tests the web layer in isolation, with the service layer mocked using @MockBean.
 */
@WebMvcTest(BannerController.class)
public class BannerControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BannerService bannerService;

    /**
     * Tests the home endpoint returns the expected message.
     * Verifies that the controller correctly calls the service and returns the response.
     */
    @Test
    void home_shouldReturnWelcomeMessage() throws Exception {
        // Arrange
        String expectedMessage = "Welcome to the Custom Startup Banner Application!";
        when(bannerService.getBannerMessage()).thenReturn(expectedMessage);

        // Act & Assert
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedMessage));
    }
}
