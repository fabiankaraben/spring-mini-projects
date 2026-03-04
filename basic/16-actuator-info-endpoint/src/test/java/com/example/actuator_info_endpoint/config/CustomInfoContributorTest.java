package com.example.actuator_info_endpoint.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test class verifying the behavior of CustomInfoContributor using JUnit
 * 5.
 * This class isolates and verifies the contribution logic decoupled from the
 * Spring context.
 * No Mockito is necessary because the operation is pure computation on basic
 * Objects.
 */
class CustomInfoContributorTest {

    @Test
    @DisplayName("Should successfully append custom details map to the Actuator Info.Builder")
    void shouldContributeCustomMetricsToInfoEndpoint() {
        // Arrange
        // We initialize our custom info contributor instance.
        CustomInfoContributor contributor = new CustomInfoContributor();
        Info.Builder builder = new Info.Builder();

        // Act
        // Invoke the core logic which is supposed to populate the builder object
        contributor.contribute(builder);
        Info info = builder.build();

        // Assert
        // Verify that custom metrics are embedded into the info map successfully
        @SuppressWarnings("unchecked")
        Map<String, Object> customDetails = (Map<String, Object>) info.get("custom");

        assertNotNull(customDetails, "The provided custom details map must not be null.");
        assertEquals(42, customDetails.get("active-users"),
                "active-users property must be present and hold correct value.");
        assertEquals("System is running optimally", customDetails.get("status"), "status property must be correct.");
        assertFalse((Boolean) customDetails.get("maintenance-mode"),
                "maintenance-mode feature toggle flag must be false.");
    }
}
