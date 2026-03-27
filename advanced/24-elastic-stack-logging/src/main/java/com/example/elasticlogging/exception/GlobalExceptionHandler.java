package com.example.elasticlogging.exception;

import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all REST controllers.
 *
 * <p>Maps domain and validation exceptions to appropriate HTTP error responses
 * and logs each failure as a structured JSON event. This ensures that:
 * <ul>
 *   <li>The client receives a well-formed JSON error body.</li>
 *   <li>The Elastic Stack receives a structured log entry with error details
 *       as first-class JSON fields (not embedded in a message string).</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles {@link OrderNotFoundException} — returns HTTP 404.
     *
     * <p>Logged as a WARN because a missing order is an expected, client-side error
     * rather than a system failure.
     *
     * @param ex the exception carrying the missing order ID
     * @return 404 Not Found with an error body
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(OrderNotFoundException ex) {
        // WARN: expected client error — orderId field lets Kibana count 404s per order
        log.warn("Order not found - returning 404",
                StructuredArguments.kv("orderId", ex.getOrderId()),
                StructuredArguments.kv("httpStatus", 404)
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        ));
    }

    /**
     * Handles Bean Validation failures (@Valid) — returns HTTP 400.
     *
     * <p>Logged as a WARN with a comma-separated list of all violated constraints
     * as a structured field. This lets Kibana identify which fields are most
     * frequently invalid.
     *
     * @param ex the validation exception from Spring MVC
     * @return 400 Bad Request with a list of validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect all field-level violation messages into a single comma-separated string
        String violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed - returning 400",
                StructuredArguments.kv("violations", violations),
                StructuredArguments.kv("httpStatus", 400)
        );

        Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST, "Validation failed");
        body.put("violations", violations);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Catch-all handler for unexpected exceptions — returns HTTP 500.
     *
     * <p>Logged as an ERROR with the exception class and message as structured fields.
     * In production this would trigger a Kibana alert.
     *
     * @param ex the unhandled exception
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericError(Exception ex) {
        log.error("Unhandled exception - returning 500",
                StructuredArguments.kv("exceptionClass", ex.getClass().getSimpleName()),
                StructuredArguments.kv("exceptionMessage", ex.getMessage()),
                StructuredArguments.kv("httpStatus", 500),
                ex
        );

        return ResponseEntity.internalServerError().body(errorBody(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        ));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a standard error response body map.
     *
     * @param status  HTTP status code
     * @param message human-readable error message
     * @return mutable map that callers can extend before returning to the client
     */
    private Map<String, Object> errorBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
