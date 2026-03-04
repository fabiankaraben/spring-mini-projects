package com.example.propertiesconfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main entry point of the application.
 * 
 * @ConfigurationPropertiesScan automatically scans and registers
 * @ConfigurationProperties classes in the application context.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PropertiesConfigurationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertiesConfigurationApplication.class, args);
    }
}
