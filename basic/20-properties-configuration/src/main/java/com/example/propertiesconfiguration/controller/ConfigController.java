package com.example.propertiesconfiguration.controller;

import com.example.propertiesconfiguration.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to expose the loaded configuration properties.
 * This demonstrates that the properties from application.properties
 * were successfully bound to our POJO.
 */
@RestController
@RequestMapping("/config")
public class ConfigController {

    private final AppProperties appProperties;

    // The AppProperties POJO is automatically injected by Spring
    public ConfigController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Endpoint to retrieve the bound properties.
     * 
     * @return the AppProperties instance serialized to JSON
     */
    @GetMapping
    public AppProperties getConfig() {
        return appProperties;
    }
}
