package com.example.autoconfiguration.service;

import com.example.autoconfiguration.dto.GreetingRequest;
import com.example.autoconfiguration.dto.GreetingResponse;
import com.example.autoconfiguration.entity.GreetingLog;
import com.example.autoconfiguration.exception.GreetingLogNotFoundException;
import com.example.autoconfiguration.repository.GreetingLogRepository;
import com.example.autoconfiguration.starter.GreetingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Application service that coordinates the auto-configured {@link GreetingService}
 * with the persistence layer ({@link GreetingLogRepository}).
 *
 * <p>This is the central component that demonstrates the custom starter in action:
 * <ol>
 *   <li>It receives the caller's name (or null) from the controller.</li>
 *   <li>It delegates to the auto-configured {@link GreetingService} to produce the
 *       greeting message — the service was configured entirely via YAML without any
 *       explicit {@code @Bean} definition in this application.</li>
 *   <li>It persists the greeting and name to PostgreSQL so the history can be queried.</li>
 *   <li>It maps the saved entity to a {@link GreetingResponse} DTO for the API layer.</li>
 * </ol>
 *
 * <p>{@code @Transactional} on write methods ensures that all database operations
 * within that method are wrapped in a single transaction — if anything fails, all
 * changes are rolled back atomically.
 */
@Service
public class GreetingLogService {

    /**
     * The auto-configured greeting service injected by Spring Boot from the
     * custom starter's {@code @AutoConfiguration} class. No explicit bean
     * definition was needed in this application — just the YAML properties
     * and the {@code AutoConfiguration.imports} file in the classpath.
     */
    private final GreetingService greetingService;

    /**
     * Spring Data JPA repository for persisting and querying greeting logs.
     */
    private final GreetingLogRepository greetingLogRepository;

    /**
     * Constructor injection — preferred over field injection because it makes
     * dependencies explicit and simplifies unit testing (no Spring context needed).
     *
     * @param greetingService      the auto-configured greeting service from the starter
     * @param greetingLogRepository the JPA repository for greeting history
     */
    public GreetingLogService(GreetingService greetingService,
                               GreetingLogRepository greetingLogRepository) {
        this.greetingService = greetingService;
        this.greetingLogRepository = greetingLogRepository;
    }

    /**
     * Generates a greeting for the given name, persists it, and returns the result.
     *
     * <p>This method is the primary use case of the application. It calls the
     * auto-configured {@link GreetingService#greet(String)} and stores the output.
     *
     * @param request the incoming greeting request (name may be null/blank)
     * @return the persisted greeting log as a response DTO
     */
    @Transactional
    public GreetingResponse greet(GreetingRequest request) {
        // Delegate to the auto-configured service to generate the greeting message
        String name = request.name();
        String message = greetingService.greet(name);

        // Determine effective name (mirrors what GreetingService does internally)
        // so we store the actual name used, not null/blank
        String effectiveName = (name == null || name.isBlank())
                ? greetingService.getProperties().getDefaultName()
                : name;

        // Persist the greeting log with the current UTC timestamp
        GreetingLog log = new GreetingLog(effectiveName, message, Instant.now());
        GreetingLog saved = greetingLogRepository.save(log);

        // Map entity to DTO for the API response
        return toResponse(saved);
    }

    /**
     * Returns all greeting logs ordered by most recent first.
     *
     * @return list of all greeting responses, newest first
     */
    @Transactional(readOnly = true)
    public List<GreetingResponse> findAll() {
        return greetingLogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns a single greeting log by its database ID.
     *
     * @param id the ID of the greeting log to retrieve
     * @return the greeting response DTO
     * @throws GreetingLogNotFoundException if no log with the given ID exists
     */
    @Transactional(readOnly = true)
    public GreetingResponse findById(Long id) {
        GreetingLog log = greetingLogRepository.findById(id)
                .orElseThrow(() -> new GreetingLogNotFoundException(id));
        return toResponse(log);
    }

    /**
     * Returns all greeting logs for a specific name.
     *
     * @param name the exact name to filter by (case-sensitive)
     * @return list of matching greeting responses, may be empty
     */
    @Transactional(readOnly = true)
    public List<GreetingResponse> findByName(String name) {
        return greetingLogRepository.findByName(name)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Deletes a greeting log by ID.
     *
     * @param id the ID of the log to delete
     * @throws GreetingLogNotFoundException if no log with the given ID exists
     */
    @Transactional
    public void delete(Long id) {
        if (!greetingLogRepository.existsById(id)) {
            throw new GreetingLogNotFoundException(id);
        }
        greetingLogRepository.deleteById(id);
    }

    /**
     * Maps a {@link GreetingLog} entity to a {@link GreetingResponse} DTO.
     *
     * <p>Centralised mapping method — all conversion from entity to DTO happens here
     * so that if the DTO shape changes, only this method needs updating.
     *
     * @param log the entity to convert
     * @return the corresponding response DTO
     */
    private GreetingResponse toResponse(GreetingLog log) {
        return new GreetingResponse(
                log.getId(),
                log.getName(),
                log.getMessage(),
                log.getCreatedAt()
        );
    }
}
