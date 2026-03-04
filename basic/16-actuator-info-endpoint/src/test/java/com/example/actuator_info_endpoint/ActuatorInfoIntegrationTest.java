package com.example.actuator_info_endpoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full Slice Integration Testing focused on the Actuator infrastructure.
 * 
 * Note: Actuator endpoints (Annotated with @Endpoint) are logically decoupled
 * from typical RequestMappingHandlerMapping and therefore do not automatically
 * load
 * under standard @WebMvcTest configurations by default without complex
 * configurations.
 * Hence, utilizing an @SpringBootTest paired with @AutoConfigureMockMvc is the
 * standardized approach
 * to test Actuator behavior End-To-End without starting a real Web Server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ActuatorInfoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should serve the default system properties and custom configurations within API response")
    void shouldReturnCompleteInfoPayloadViaActuatorEndpoint() throws Exception {
        // Execute GET Request explicitly toward the /actuator/info HTTP Interface
        mockMvc.perform(get("/actuator/info")
                .accept(MediaType.APPLICATION_JSON))
                // HTTP Response status should be explicitly 200 OK
                .andExpect(status().isOk())
                // Verify Standard configured Properties injected via `application.properties`
                .andExpect(jsonPath("$.app.name").value("actuator-info-endpoint"))
                .andExpect(jsonPath("$.app.version").value("1.0.0"))
                .andExpect(jsonPath("$.app.team").value("backend-team"))
                // Expect Java runtime logic injection via `management.info.java.enabled`
                .andExpect(jsonPath("$.java.version").exists())
                // Expect custom configurations loaded via our CustomInfoContributor.java
                .andExpect(jsonPath("$.custom.active-users").value(42))
                .andExpect(jsonPath("$.custom.status").value("System is running optimally"))
                .andExpect(jsonPath("$.custom.maintenance-mode").value(false));
    }
}
