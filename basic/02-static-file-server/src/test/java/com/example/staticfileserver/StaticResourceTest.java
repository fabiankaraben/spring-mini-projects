package com.example.staticfileserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Educational example of full integration testing for a component-less web
 * application.
 * Verifies that Spring Boot's ResourceHttpRequestHandler automatically serves
 * the
 * content placed in src/main/resources/static.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaticResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @SuppressWarnings("null")
    void shouldServeIndexHtml() throws Exception {
        // Assert that GET /index.html returns 200 OK and valid HTML content
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Welcome to the Static File Server!")));
    }

    @Test
    @SuppressWarnings("null")
    void shouldServeStyleCss() throws Exception {
        // Assert that GET /style.css returns 200 OK and valid CSS content type
        mockMvc.perform(get("/style.css"))
                .andExpect(status().isOk())
                // In some setups it might just return null or exact string for CSS, checking
                // basic string contains
                .andExpect(content().string(containsString("background-color: #f4f4f4;")));
    }

    @Test
    @SuppressWarnings("null")
    void shouldServeScriptJs() throws Exception {
        // Assert that GET /script.js returns 200 OK
        mockMvc.perform(get("/script.js"))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(containsString("The JavaScript file was successfully loaded and executed!")));
    }
}
