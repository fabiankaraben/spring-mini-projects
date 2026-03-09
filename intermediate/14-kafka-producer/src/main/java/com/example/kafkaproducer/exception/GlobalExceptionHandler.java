package com.example.kafkaproducer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised error-handling advice for all REST controllers.
 *
 * <p>{@code @RestControllerAdvice} is a specialisation of {@code @ControllerAdvice}
 * that applies {@code @ResponseBody} to every {@code @ExceptionHandler} method,
 * so the returned objects are automatically serialised to JSON.
 *
 * <p>Centralising exception handling here keeps the controllers clean:
 * controllers never need {@code try/catch} blocks for predictable error cases.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation failures triggered by {@code @Valid} on request bodies.
     *
     * <p>Spring MVC throws {@link MethodArgumentNotValidException} when a request
     * body fails validation. This handler collects all field-level error messages
     * into a map and returns them as a {@code 400 Bad Request} response.
     *
     * <p>Example response body:
     * <pre>{@code
     * {
     *   "status": 400,
     *   "error": "Validation failed",
     *   "fields": {
     *     "orderId":    "orderId must not be blank",
     *     "quantity":   "quantity must be at least 1"
     *   },
     *   "timestamp": "2024-01-01T00:00:00Z"
     * }
     * }</pre>
     *
     * @param ex the validation exception containing the binding result
     * @return a {@code 400} response with per-field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect each field's default message into a map keyed by field name
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation failed");
        body.put("fields", fieldErrors);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles unexpected runtime errors, including Kafka send failures.
     *
     * <p>Any {@link RuntimeException} that propagates out of a controller method
     * is caught here and mapped to a {@code 500 Internal Server Error} response.
     * The exception message is included to aid debugging, but a production
     * system should mask sensitive details and log the full stack trace instead.
     *
     * @param ex the runtime exception
     * @return a {@code 500} response with a short error description
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", ex.getMessage());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
