package com.example.cookies.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * Tests for CookieController demonstrating both pure Unit testing with Mockito
 * and Sliced Integration testing with @WebMvcTest.
 */
public class CookieControllerTest {

    /**
     * Sliced Integration Test for the Web layer using @WebMvcTest.
     * This loads only the web layer components (like Controllers) and ignores
     * services, repos, etc.
     */
    @WebMvcTest(CookieController.class)
    @Nested
    class WebLayerIntegrationTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void setCookie_shouldSetCookieInResponse() throws Exception {
            // We simulate a POST request to our set cookie endpoint
            mockMvc.perform(post("/api/cookies/set")
                    .param("value", "dark_mode"))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists("user_preference"))
                    .andExpect(cookie().value("user_preference", "dark_mode"))
                    .andExpect(cookie().httpOnly("user_preference", true))
                    .andExpect(cookie().path("user_preference", "/"))
                    .andExpect(cookie().maxAge("user_preference", 7 * 24 * 60 * 60))
                    .andExpect(content().string("Cookie 'user_preference' has been set with value: dark_mode"));
        }

        @Test
        void readCookie_whenCookieExists_shouldReturnCookieValue() throws Exception {
            // We supply a mocked cookie in our GET request
            Cookie mockCookie = new Cookie("user_preference", "light_mode");

            mockMvc.perform(get("/api/cookies/read")
                    .cookie(mockCookie))
                    .andExpect(status().isOk())
                    .andExpect(content().string("The read value of 'user_preference' is: light_mode"));
        }

        @Test
        void readCookie_whenCookieIsMissing_shouldReturnDefaultValue() throws Exception {
            // We perform a GET request without any cookie attached
            mockMvc.perform(get("/api/cookies/read"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("The read value of 'user_preference' is: No cookie found"));
        }
    }

    /**
     * Pure Unit Test using JUnit 5 and Mockito.
     * This tests the logic of the Controller methods directly without loading
     * Spring context.
     */
    @ExtendWith(MockitoExtension.class)
    @Nested
    class PureUnitTests {

        @Mock
        private HttpServletResponse response;

        @InjectMocks
        private CookieController controller;

        @Test
        void setCookie_shouldAddCookieToResponse() {
            // Execute the controller method
            ResponseEntity<String> result = controller.setCookie("test_value", response);

            // Assert the response body and status
            assertEquals(200, result.getStatusCode().value());
            assertEquals("Cookie 'user_preference' has been set with value: test_value", result.getBody());

            // Use ArgumentCaptor to capture the cookie added to the response
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            Cookie capturedCookie = cookieCaptor.getValue();

            // Verify all properties of the created cookie via assertions
            assertEquals("user_preference", capturedCookie.getName());
            assertEquals("test_value", capturedCookie.getValue());
            assertTrue(capturedCookie.isHttpOnly());
            assertEquals("/", capturedCookie.getPath());
            assertEquals(604800, capturedCookie.getMaxAge()); // 7 days in seconds
        }

        @Test
        void readCookie_shouldReturnCorrectValueString() {
            // We test the method directly, providing parameters manually
            ResponseEntity<String> result = controller.readCookie("mocked_value");

            assertEquals(200, result.getStatusCode().value());
            assertEquals("The read value of 'user_preference' is: mocked_value", result.getBody());
        }
    }
}
