package com.example.optimisticlocking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception-to-HTTP-response mapping for the REST API.
 *
 * <p>{@code @RestControllerAdvice} applies this handler to every
 * {@code @RestController} in the application context. Each {@code @ExceptionHandler}
 * method intercepts a specific exception type and returns a consistent JSON error body.</p>
 *
 * <h2>Handled exceptions</h2>
 * <ul>
 *   <li>{@link ProductNotFoundException} → 404 Not Found</li>
 *   <li>{@link ObjectOptimisticLockingFailureException} → 409 Conflict (the core
 *       concern of this mini-project)</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (Bean Validation)</li>
 *   <li>{@link Exception} (fallback) → 500 Internal Server Error</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    /**
     * Handles {@link ProductNotFoundException} thrown by the service when a product
     * with the requested ID does not exist.
     *
     * @param ex the exception carrying the error message
     * @return 404 with a JSON error body
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductNotFound(ProductNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorBody(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    // ── 409 Conflict (Optimistic Locking) ─────────────────────────────────────

    /**
     * Handles {@link ObjectOptimisticLockingFailureException}, which Spring throws
     * when Hibernate detects that the version of the entity being updated no longer
     * matches the version in the database.
     *
     * <h2>Why this returns 409 Conflict</h2>
     * <p>HTTP 409 Conflict is the correct status code for a "state conflict" between
     * the client's request and the current state of the resource on the server.
     * The client attempted to update based on stale data (an outdated version), and
     * the server is signalling that it cannot process the request in its current state.</p>
     *
     * <h2>What the client should do</h2>
     * <ol>
     *   <li>Re-fetch the latest version of the resource (GET).</li>
     *   <li>Re-apply the desired changes to the fresh data.</li>
     *   <li>Retry the PUT request with the updated {@code version} value.</li>
     * </ol>
     *
     * @param ex the exception thrown by Spring's ORM layer
     * @return 409 with a descriptive JSON error body
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex) {

        String message = "Concurrent modification detected: the product was updated by another " +
                "request since you last fetched it. Please re-fetch the latest version and retry.";

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorBody(HttpStatus.CONFLICT, message));
    }

    // ── 400 Bad Request (Bean Validation) ─────────────────────────────────────

    /**
     * Handles {@link MethodArgumentNotValidException} thrown by Spring MVC when
     * {@code @Valid} validation fails on a request body.
     *
     * <p>The response includes a {@code fieldErrors} map so clients know exactly
     * which fields failed and why.</p>
     *
     * @param ex the validation exception containing per-field error details
     * @return 400 with a JSON body containing a {@code fieldErrors} map
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // Collect one error message per field (first violation wins if multiple)
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (first, second) -> first   // keep first message if duplicate keys
                ));

        Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST, "Validation failed");
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── 500 Internal Server Error (fallback) ──────────────────────────────────

    /**
     * Catch-all handler for unexpected exceptions.
     *
     * <p>Returns a generic 500 response without leaking internal details to the client.</p>
     *
     * @param ex the unexpected exception
     * @return 500 with a generic error body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"));
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    /**
     * Builds a consistent JSON error body with status, error description, message, and timestamp.
     *
     * @param status  the HTTP status being returned
     * @param message a human-readable description of the error
     * @return a mutable map suitable for use as a JSON response body
     */
    private Map<String, Object> errorBody(HttpStatus status, String message) {
        // Using a mutable map so callers can add extra fields (e.g. fieldErrors)
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
