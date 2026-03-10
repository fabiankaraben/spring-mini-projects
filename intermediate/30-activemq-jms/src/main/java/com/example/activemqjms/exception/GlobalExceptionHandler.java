package com.example.activemqjms.exception;

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
 * Global exception handler for REST controllers.
 *
 * <p>{@link RestControllerAdvice} makes this class apply to all controllers
 * in the application. It centralises error response formatting so every
 * endpoint returns consistent, structured error bodies.
 *
 * <h2>Handled exceptions</h2>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — thrown by Spring MVC when
 *       {@code @Valid} on a request body parameter finds constraint violations.
 *       Returns HTTP 400 Bad Request with a field-level error map.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle Bean Validation failures on {@code @RequestBody} parameters.
     *
     * <p>When a client sends a request that fails validation (e.g. blank
     * {@code orderId} or {@code quantity < 1}), Spring MVC throws this
     * exception before the controller method body is executed.
     *
     * <p>Response body example:
     * <pre>{@code
     * {
     *   "timestamp": "2024-03-15T10:15:30Z",
     *   "status": 400,
     *   "errors": {
     *     "orderId": "orderId must not be blank",
     *     "quantity": "quantity must be at least 1"
     *   }
     * }
     * }</pre>
     *
     * @param ex the exception containing all constraint violations
     * @return 400 Bad Request with a structured JSON body listing the field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect all field-level validation errors into a map: fieldName → errorMessage
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        // Build a structured response body with timestamp, status, and the field errors
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errors", fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }
}
