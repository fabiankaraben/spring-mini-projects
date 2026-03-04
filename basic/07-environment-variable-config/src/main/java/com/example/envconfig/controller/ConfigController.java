package com.example.envconfig.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller that reads and exposes configuration properties.
 * It demonstrates reading configuration via {@link Value @Value} annotation
 * and the {@link Environment Environment} component.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    // Read a value using @Value.
    // If "app.name" property or "APP_NAME" environment variable is not set,
    // it defaults to "Default App Name".
    @Value("${app.name:Default App Name}")
    private String appName;

    // Use the Spring Environment to programmatically access properties and
    // environment variables.
    private final Environment environment;

    /**
     * Constructor injection for Environment.
     * 
     * @param environment the Spring Environment object
     */
    public ConfigController(Environment environment) {
        this.environment = environment;
    }

    /**
     * Endpoint to fetch the application configuration.
     * 
     * @return a map containing configuration values resolved from @Value and
     *         Environment
     */
    @GetMapping
    public Map<String, String> getConfig() {
        Map<String, String> configData = new HashMap<>();

        // 1. Reading the property fetched by @Value
        configData.put("appName", this.appName);

        // 2. Reading a property/variable explicitly using the Environment bean.
        // It reads the property 'app.version' or the env variable 'APP_VERSION'.
        // Uses "1.0.0" as a fallback value.
        String appVersion = environment.getProperty("APP_VERSION", "1.0.0");
        configData.put("appVersion", appVersion);

        // 3. Example reading an execution environment mode
        String envMode = environment.getProperty("ENV_MODE", "development");
        configData.put("envMode", envMode);

        return configData;
    }
}
