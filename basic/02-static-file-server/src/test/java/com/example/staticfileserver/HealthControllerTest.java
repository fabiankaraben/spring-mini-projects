package com.example.staticfileserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Educational example of Sliced Integration Testing.
 * Uses {@link WebMvcTest} to load only the web layer components required for
 * {@link HealthController}.
 * Uses {@link MockBean} to provide a mocked instance of {@link HealthService}
 * via Mockito.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthService healthService;

    @Test
    void shouldReturnHealthStatus() throws Exception {
        // Given
        String expectedStatus = "Mocked System is OK!";
        when(healthService.getStatus()).thenReturn(expectedStatus);

        // When & Then
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedStatus));
    }
}
