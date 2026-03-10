package com.example.softdelete.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception handler for all REST controllers.
 *
 * <p>{@code @RestControllerAdvice} makes this class an AOP advice applied to all
 * {@code @RestController} classes.  Each {@code @ExceptionHandler} method intercepts
 * a specific exception type, builds an appropriate HTTP response body, and returns
 * the correct HTTP status code without duplicating error-handling logic in every
 * controller.</p>
 *
 * <h2>Error response format</h2>
 * <p>All error responses use a consistent JSON structure:</p>
 * <pre>{@code
 * {
 *   "timestamp": "2024-01-01T12:00:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Product not found with id: 42"
 * }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@link ProductNotFoundException}, returning 404 Not Found.
     *
     * <p>This is triggered whenever a product lookup fails – both for truly absent
     * products and for soft-deleted ones (which are invisible to normal queries).</p>
     *
     * @param ex the exception thrown by the service layer
     * @return 404 response with a descriptive message
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductNotFound(ProductNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles {@link MethodArgumentNotValidException}, returning 400 Bad Request.
     *
     * <p>Spring's Bean Validation throws this exception when a {@code @Valid}-annotated
     * request body fails validation constraints (e.g. blank name, negative price).
     * The response body lists each field's validation error.</p>
     *
     * @param ex the validation exception containing field errors
     * @return 400 response with a map of field name → error message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect all field-level validation errors into a readable map
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Catch-all handler for any unexpected exception.
     *
     * <p>This prevents stack traces from leaking into API responses, which would be
     * a security concern in production.  In development, the message is included;
     * production deployments should log the exception and return a generic message.</p>
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred: " + ex.getMessage());
    }

    /**
     * Helper that builds a standard error response map.
     *
     * @param status  the HTTP status to return
     * @param message the human-readable error message
     * @return a {@link ResponseEntity} with the error body
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
