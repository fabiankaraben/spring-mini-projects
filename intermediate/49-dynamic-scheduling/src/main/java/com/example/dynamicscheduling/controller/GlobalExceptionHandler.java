package com.example.dynamicscheduling.controller;

import com.example.dynamicscheduling.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler that converts exceptions thrown by the service layer
 * or Bean Validation into structured JSON error responses.
 *
 * <p>Without this handler, Spring Boot would return its default Whitelabel error
 * page or a generic JSON error object.  By using {@link RestControllerAdvice}
 * we intercept exceptions and return our own {@link ApiResponse} format so all
 * error responses share the same {@code success/message} structure as success ones.
 *
 * <h2>Handled exceptions</h2>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} – raised by {@code @Valid} when
 *       a request body fails Bean Validation constraints. Returns HTTP 400.</li>
 *   <li>{@link IllegalArgumentException} – thrown by the service layer for not-found
 *       tasks, duplicate names, or invalid state transitions. Returns HTTP 404 when
 *       the message contains "not found", otherwise HTTP 400.</li>
 *   <li>{@link Exception} – catch-all for unexpected server errors. Returns HTTP 500.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Bean Validation failures ──────────────────────────────────────────────────

    /**
     * Handles {@link MethodArgumentNotValidException} raised when a {@code @Valid}
     * annotated request body fails Bean Validation.
     *
     * <p>Collects all field-level error messages into a single comma-separated
     * string so the caller receives a clear description of every problem at once.
     *
     * @param ex the validation exception
     * @return HTTP 400 with a consolidated error message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        // Collect all field validation messages into one readable string
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    // ── Business logic errors ─────────────────────────────────────────────────────

    /**
     * Handles {@link IllegalArgumentException} thrown by the service layer.
     *
     * <p>The HTTP status depends on the exception message:
     * <ul>
     *   <li>Messages containing {@code "not found"} → 404 Not Found</li>
     *   <li>All other messages → 400 Bad Request</li>
     * </ul>
     *
     * @param ex the exception thrown by the service
     * @return HTTP 404 or 400 with the exception message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Business logic error: {}", ex.getMessage());

        // "not found" messages map to 404; everything else maps to 400
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body(ApiResponse.error(ex.getMessage()));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────────

    /**
     * Catch-all handler for any unexpected exception not covered above.
     *
     * <p>Returns HTTP 500 and logs the full stack trace so operators can
     * investigate.  The response body intentionally omits internal details
     * to avoid leaking implementation information to callers.
     *
     * @param ex the unexpected exception
     * @return HTTP 500 with a generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(ApiResponse.error("An unexpected error occurred"));
    }
}
