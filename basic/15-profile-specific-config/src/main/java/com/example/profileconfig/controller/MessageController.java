package com.example.profileconfig.controller;

import com.example.profileconfig.service.MessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller showcasing the use of environment-specific properties and beans.
 * This class exposes an API that returns a JSON detailing which profile is
 * active.
 */
@RestController
@RequestMapping("/api")
public class MessageController {

    private final MessageService messageService;

    /**
     * The @Value annotation is used here to inject a property defined in our
     * application properties.
     * The notation "${app.environment.name:Default}" means: look for
     * "app.environment.name" in
     * the active properties file. If it's not found, fallback to the string
     * "Default".
     *
     * In a real application, different environments (dev, prod) will provide
     * different values.
     */
    @Value("${app.environment.name:Default}")
    private String environmentName;

    /**
     * Constructor-based dependency injection.
     * Spring looks at the active profiles and injects the corresponding bean
     * implementation (either DevMessageService, ProdMessageService, or
     * DefaultMessageService).
     *
     * @param messageService the active MessageService implementation
     */
    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Exposes a GET endpoint.
     * It combines the property value (@Value) and the bean output (MessageService)
     * to show the complete picture of what got configured.
     *
     * @return A map resulting in a JSON response with status properties.
     */
    @GetMapping("/status")
    public Map<String, String> getStatus() {
        return Map.of(
                "environment", environmentName,
                "message", messageService.getMessage());
    }
}
