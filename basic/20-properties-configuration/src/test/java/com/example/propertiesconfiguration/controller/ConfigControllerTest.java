package com.example.propertiesconfiguration.controller;

import com.example.propertiesconfiguration.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sliced integration test testing just the web layer using @WebMvcTest.
 * This guarantees the Controller correctly serializes the AppProperties object
 * to JSON.
 */
@WebMvcTest(ConfigController.class)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Based on user rules, we use @MockitoBean for replacing a real bean with a
    // mock.
    // It replaces the deprecated @MockBean
    @MockitoBean
    private AppProperties appProperties;

    @Test
    void getConfig_ReturnsSerializedAppProperties() throws Exception {
        // Arrange
        AppProperties.Developer mockDev = new AppProperties.Developer();
        mockDev.setName("Test Dev");
        mockDev.setEmail("test@test.com");

        AppProperties.Features mockFeatures = new AppProperties.Features();
        mockFeatures.setEnabled(false);
        mockFeatures.setMaxUsers(10);

        Mockito.when(appProperties.getName()).thenReturn("Mocked App");
        Mockito.when(appProperties.getVersion()).thenReturn("9.9.9");
        Mockito.when(appProperties.getDeveloper()).thenReturn(mockDev);
        Mockito.when(appProperties.getFeatures()).thenReturn(mockFeatures);

        // Act & Assert
        mockMvc.perform(get("/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mocked App"))
                .andExpect(jsonPath("$.version").value("9.9.9"))
                .andExpect(jsonPath("$.developer.name").value("Test Dev"))
                .andExpect(jsonPath("$.developer.email").value("test@test.com"))
                .andExpect(jsonPath("$.features.enabled").value(false))
                .andExpect(jsonPath("$.features.maxUsers").value(10));
    }
}
