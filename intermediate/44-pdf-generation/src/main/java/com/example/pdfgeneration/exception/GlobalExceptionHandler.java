package com.example.pdfgeneration.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Centralised exception handler for the PDF Generation API.
 *
 * <p>{@code @RestControllerAdvice} is a specialisation of {@code @ControllerAdvice}
 * that applies to all {@code @RestController} classes. Every {@code @ExceptionHandler}
 * method here intercepts the matching exception type thrown anywhere in the controller
 * layer and converts it to a structured JSON error response.
 *
 * <p>Centralising error handling here keeps controllers clean (no try/catch blocks)
 * and ensures all error responses follow the same JSON shape:
 * <pre>{@code
 * {
 *   "timestamp": "2024-06-15T12:00:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "...",
 *   "path": "..."      // present on some errors
 * }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 400 Bad Request – Bean Validation failures ────────────────────────────

    /**
     * Handles validation errors raised by {@code @Valid} on request bodies.
     *
     * <p>Spring throws {@link MethodArgumentNotValidException} when Bean Validation
     * detects constraint violations in a {@code @RequestBody} parameter. This handler
     * collects all field-level errors into a map and returns {@code 400 Bad Request}.
     *
     * @param ex the validation exception containing all constraint violations
     * @return a map of field names to their violation messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect each field's first validation message
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> body = buildErrorBody(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "One or more fields failed validation");
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    // ── 400 Bad Request – business rule violations ────────────────────────────

    /**
     * Handles business-rule violations thrown by the service layer.
     *
     * <p>For example, attempting to create an invoice with a duplicate invoice
     * number throws {@link IllegalArgumentException} in {@code InvoiceService}.
     *
     * @param ex the exception with a human-readable message
     * @return {@code 400 Bad Request} with the exception message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {

        return ResponseEntity.badRequest()
                .body(buildErrorBody(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        ex.getMessage()));
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    /**
     * Handles "not found" conditions thrown when looking up invoices by id.
     *
     * <p>{@link NoSuchElementException} is thrown by {@code InvoiceService} when
     * a requested invoice id does not exist in the database.
     *
     * @param ex the exception with a human-readable message
     * @return {@code 404 Not Found} with the exception message
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            NoSuchElementException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorBody(
                        HttpStatus.NOT_FOUND.value(),
                        "Not Found",
                        ex.getMessage()));
    }

    // ── 500 Internal Server Error – catch-all ─────────────────────────────────

    /**
     * Catch-all handler for any unexpected exception not matched by the handlers above.
     *
     * <p>Returns a generic {@code 500 Internal Server Error} response to avoid
     * leaking implementation details (stack traces) to the client.
     *
     * @param ex the unexpected exception
     * @return {@code 500 Internal Server Error} with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorBody(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error",
                        "An unexpected error occurred. Please try again later."));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Builds a standard error response body map shared across all handlers.
     *
     * @param status  the HTTP status code (integer)
     * @param error   short error label (e.g. "Not Found")
     * @param message detailed, human-readable error description
     * @return a mutable map that callers can enrich with additional fields
     */
    private Map<String, Object> buildErrorBody(int status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        return body;
    }
}
