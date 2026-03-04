package com.example.envconfig.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

// Sliced integration tests
// Tests only the web layer mapped to Spring's web environment context.
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConfigController.class, properties = {
        "app.name=Test App Name",
        "APP_VERSION=2.5.0",
        "ENV_MODE=testing"
})
public class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnConfigWithInjectedProperties() throws Exception {
        // Here we test the getConfig method and check the returned JSON object.
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("Test App Name"))
                .andExpect(jsonPath("$.appVersion").value("2.5.0"))
                .andExpect(jsonPath("$.envMode").value("testing"));
    }
}
