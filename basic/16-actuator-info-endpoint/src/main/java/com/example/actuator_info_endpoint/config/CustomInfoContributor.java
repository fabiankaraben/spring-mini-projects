package com.example.actuator_info_endpoint.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom Information Contributor for the /actuator/info endpoint.
 * This component implements InfoContributor to inject custom metrics and
 * details
 * into the standard Actuator info JSON response.
 */
@Component
public class CustomInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        // Create a map to hold our custom details that we want to expose
        Map<String, Object> customDetails = new HashMap<>();

        // Populate the map with various examples of data
        customDetails.put("active-users", 42); // Simulated metric
        customDetails.put("status", "System is running optimally"); // Informative status
        customDetails.put("maintenance-mode", false); // Feature toggle/status

        // Add the custom details under the "custom" node in the info endpoint.
        // It will appear as part of the JSON response at /actuator/info.
        builder.withDetail("custom", customDetails);
    }
}
