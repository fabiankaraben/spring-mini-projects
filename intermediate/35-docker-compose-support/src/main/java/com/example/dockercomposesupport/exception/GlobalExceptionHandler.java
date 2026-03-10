package com.example.dockercomposesupport.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all REST controllers.
 *
 * <p>{@code @RestControllerAdvice} makes this class act as a global interceptor
 * that catches exceptions thrown by any {@code @RestController} and converts them
 * into structured JSON error responses — preventing stack traces from leaking to
 * the client and keeping error handling out of the business logic.</p>
 *
 * <h2>Handled exceptions</h2>
 * <ul>
 *   <li>{@link BookNotFoundException} → HTTP 404 Not Found</li>
 *   <li>{@link DuplicateBookException} → HTTP 409 Conflict</li>
 *   <li>{@link MethodArgumentNotValidException} → HTTP 400 Bad Request
 *       (triggered by {@code @Valid} on controller method parameters)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@link BookNotFoundException} and returns a 404 response.
     *
     * @param ex the exception thrown when a book is not found
     * @return a JSON error body with status 404
     */
    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBookNotFound(BookNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles {@link DuplicateBookException} and returns a 409 response.
     *
     * @param ex the exception thrown when a duplicate title or ISBN is detected
     * @return a JSON error body with status 409
     */
    @ExceptionHandler(DuplicateBookException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateBook(DuplicateBookException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles Bean Validation failures (triggered by {@code @Valid}) and returns a 400 response.
     *
     * <p>The response body includes a {@code violations} map that lists each
     * invalid field together with its validation error message, which is very
     * helpful for API clients and for educational purposes.</p>
     *
     * @param ex the validation exception containing all field errors
     * @return a JSON error body with status 400 and field-level details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Collect all field-level validation errors into a map: fieldName -> errorMessage
        Map<String, String> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        // If the same field has multiple violations, concatenate them
                        (a, b) -> a + "; " + b
                ));

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation failed");
        body.put("violations", violations);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Builds a standard error response body.
     *
     * @param status  the HTTP status to return
     * @param message the error message to include
     * @return a {@link ResponseEntity} with a structured error body
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
