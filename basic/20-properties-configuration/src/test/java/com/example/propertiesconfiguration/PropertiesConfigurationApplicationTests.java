package com.example.propertiesconfiguration;

import com.example.propertiesconfiguration.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify that the application context loads
 * and that the properties from application.properties are properly
 * bound to the AppProperties bean.
 */
@SpringBootTest
class PropertiesConfigurationApplicationTests {

    @Autowired
    private AppProperties appProperties;

    @Test
    void contextLoads() {
        // Assert that the AppProperties bean was created
        assertThat(appProperties).isNotNull();
    }

    @Test
    void appPropertiesAreBoundCorrectly() {
        // Here we test if the custom properties were injected
        assertThat(appProperties.getName()).isEqualTo("My Spring Boot Utility");
        assertThat(appProperties.getVersion()).isEqualTo("1.0.0");
        assertThat(appProperties.getDescription())
                .isEqualTo("This is a sample app for demonstrating @ConfigurationProperties");

        // Nested properties
        assertThat(appProperties.getDeveloper().getName()).isEqualTo("Fabian");
        assertThat(appProperties.getDeveloper().getEmail()).isEqualTo("fabian@example.com");

        assertThat(appProperties.getFeatures().isEnabled()).isTrue();
        assertThat(appProperties.getFeatures().getMaxUsers()).isEqualTo(500);
    }

}
