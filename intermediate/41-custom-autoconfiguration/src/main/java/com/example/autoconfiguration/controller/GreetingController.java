package com.example.autoconfiguration.controller;

import com.example.autoconfiguration.dto.GreetingRequest;
import com.example.autoconfiguration.dto.GreetingResponse;
import com.example.autoconfiguration.service.GreetingLogService;
import com.example.autoconfiguration.starter.GreetingProperties;
import com.example.autoconfiguration.starter.GreetingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing the greeting API endpoints.
 *
 * <p>This controller demonstrates three things:
 * <ol>
 *   <li>The auto-configured {@link GreetingService} being used transparently — no
 *       {@code @Bean} definition was needed in this application to have it available.</li>
 *   <li>The {@link GreetingProperties} being injected directly for the diagnostic
 *       endpoint ({@code GET /api/greetings/config}), showing how configuration
 *       properties are accessible anywhere in the application.</li>
 *   <li>Standard Spring MVC patterns: {@code @RestController}, {@code @RequestMapping},
 *       HTTP method annotations, request body validation, and path variables.</li>
 * </ol>
 *
 * <p><b>Endpoints:</b>
 * <pre>
 *   POST   /api/greetings          — generate and persist a greeting
 *   GET    /api/greetings          — list all greeting logs (newest first)
 *   GET    /api/greetings/{id}     — get a specific greeting log by ID
 *   GET    /api/greetings/search   — search greeting logs by name
 *   DELETE /api/greetings/{id}     — delete a greeting log
 *   GET    /api/greetings/config   — show the current auto-configuration properties
 * </pre>
 */
@RestController
@RequestMapping("/api/greetings")
public class GreetingController {

    /**
     * The application service that delegates to the auto-configured {@link GreetingService}.
     */
    private final GreetingLogService greetingLogService;

    /**
     * The auto-configured properties bean — injected here to expose a diagnostic
     * endpoint that shows the current starter configuration. This demonstrates that
     * {@code @ConfigurationProperties} beans are full Spring beans and can be
     * injected anywhere.
     */
    private final GreetingProperties greetingProperties;

    /**
     * Constructor injection with both dependencies.
     *
     * @param greetingLogService  the application service
     * @param greetingProperties  the auto-configured properties from the starter
     */
    public GreetingController(GreetingLogService greetingLogService,
                               GreetingProperties greetingProperties) {
        this.greetingLogService = greetingLogService;
        this.greetingProperties = greetingProperties;
    }

    /**
     * Generates a greeting for the given name and persists it to the database.
     *
     * <p>The {@link GreetingService} (auto-configured by the starter) computes the
     * greeting message using the configured prefix and suffix. Omitting the request
     * body (or setting {@code name} to null/blank) triggers the configured default name.
     *
     * @param request the greeting request with an optional {@code name} field
     * @return HTTP 201 Created with the persisted greeting log
     */
    @PostMapping
    public ResponseEntity<GreetingResponse> greet(@Valid @RequestBody GreetingRequest request) {
        GreetingResponse response = greetingLogService.greet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns all greeting logs ordered by most recent first.
     *
     * @return HTTP 200 OK with list of all greeting responses
     */
    @GetMapping
    public ResponseEntity<List<GreetingResponse>> findAll() {
        return ResponseEntity.ok(greetingLogService.findAll());
    }

    /**
     * Returns a single greeting log by its database ID.
     *
     * @param id the database ID of the greeting log
     * @return HTTP 200 OK with the greeting response, or HTTP 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<GreetingResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(greetingLogService.findById(id));
    }

    /**
     * Searches greeting logs by the greeted name.
     *
     * @param name the name to search for (case-sensitive)
     * @return HTTP 200 OK with a list of matching greeting responses
     */
    @GetMapping("/search")
    public ResponseEntity<List<GreetingResponse>> findByName(@RequestParam String name) {
        return ResponseEntity.ok(greetingLogService.findByName(name));
    }

    /**
     * Deletes a greeting log by its database ID.
     *
     * @param id the database ID of the greeting log to delete
     * @return HTTP 204 No Content, or HTTP 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        greetingLogService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Diagnostic endpoint that exposes the current auto-configuration property values.
     *
     * <p>This endpoint demonstrates that the {@link GreetingProperties} bean (registered
     * via {@code @EnableConfigurationProperties} in the auto-configuration class) is a
     * first-class Spring bean that can be injected and used anywhere in the application.
     *
     * <p>Example response:
     * <pre>{@code
     * {
     *   "enabled": true,
     *   "prefix": "Hello",
     *   "suffix": "!",
     *   "defaultName": "World",
     *   "sampleGreeting": "Hello, World!"
     * }
     * }</pre>
     *
     * @return HTTP 200 OK with a map of current configuration values
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        // Build a response map that shows all current property values.
        // This is purely for educational/diagnostic purposes — in production
        // you would use /actuator/health or /actuator/env instead.
        Map<String, Object> config = Map.of(
                "enabled", greetingProperties.isEnabled(),
                "prefix", greetingProperties.getPrefix(),
                "suffix", greetingProperties.getSuffix(),
                "defaultName", greetingProperties.getDefaultName(),
                "sampleGreeting", greetingProperties.getPrefix()
                        + ", " + greetingProperties.getDefaultName()
                        + greetingProperties.getSuffix()
        );
        return ResponseEntity.ok(config);
    }
}
