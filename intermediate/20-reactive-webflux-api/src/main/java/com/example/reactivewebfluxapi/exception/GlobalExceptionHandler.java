package com.example.reactivewebfluxapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler for the reactive WebFlux API.
 *
 * <p>{@code @RestControllerAdvice} is a specialisation of {@code @ControllerAdvice}
 * that applies to all {@code @RestController} classes and automatically wraps return
 * values in a response body (no need for {@code @ResponseBody} on each handler).
 *
 * <p>In WebFlux, exception handlers must return reactive types ({@link Mono} or
 * {@link Flux}) to integrate with the non-blocking pipeline. Returning a plain
 * {@link ResponseEntity} from an {@code @ExceptionHandler} is also supported
 * because WebFlux wraps it in a {@code Mono} internally.
 *
 * <p>Advantages of a centralised handler over per-controller try/catch blocks:
 * <ul>
 *   <li>Consistent error response format across all endpoints.</li>
 *   <li>No duplication of error-handling logic.</li>
 *   <li>Clean controller code — controllers focus only on the happy path.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle 404 Not Found and other {@link ResponseStatusException} errors.
     *
     * <p>{@link ResponseStatusException} is thrown by controller methods (via
     * {@code Mono.error(new ResponseStatusException(...))}) to signal HTTP error
     * conditions such as "resource not found" or "bad request".
     *
     * @param ex the exception carrying the HTTP status and reason
     * @return a structured JSON error response with the appropriate HTTP status
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleResponseStatusException(
            ResponseStatusException ex) {
        // Build a structured error body (consistent across all API errors)
        Map<String, Object> body = buildErrorBody(
                ex.getStatusCode().value(),
                ex.getReason() != null ? ex.getReason() : ex.getMessage()
        );
        return Mono.just(ResponseEntity.status(ex.getStatusCode()).body(body));
    }

    /**
     * Handle Bean Validation failures (HTTP 400 Bad Request).
     *
     * <p>{@link WebExchangeBindException} is thrown by WebFlux when a request body
     * annotated with {@code @Valid} fails Bean Validation constraints. It contains
     * all violated constraints so we can return descriptive error messages.
     *
     * @param ex the exception containing all validation errors
     * @return a 400 response with a list of field-level validation error messages
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationException(
            WebExchangeBindException ex) {
        // Collect all field error messages into a list
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        Map<String, Object> body = buildErrorBody(HttpStatus.BAD_REQUEST.value(), "Validation failed");
        // Add the list of individual field errors
        body.put("errors", errors);

        return Mono.just(ResponseEntity.badRequest().body(body));
    }

    /**
     * Catch-all handler for any unexpected exception.
     *
     * <p>Returns HTTP 500 Internal Server Error. In production this would also
     * trigger an alert and mask internal details from the client response.
     *
     * @param ex the unexpected exception
     * @return a generic 500 error response
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericException(Exception ex) {
        Map<String, Object> body = buildErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred"
        );
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body));
    }

    /**
     * Build a standardised error response body map.
     *
     * <p>The structure matches common REST API conventions:
     * <pre>
     * {
     *   "timestamp": "2025-01-01T12:00:00Z",
     *   "status": 404,
     *   "error": "Not Found",
     *   "message": "Article not found: abc123"
     * }
     * </pre>
     *
     * @param status  the HTTP status code as an integer
     * @param message a human-readable description of the error
     * @return a mutable map representing the JSON error body
     */
    private Map<String, Object> buildErrorBody(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        // Resolve a standard HTTP reason phrase from the numeric status code
        HttpStatus httpStatus = HttpStatus.resolve(status);
        body.put("error", httpStatus != null ? httpStatus.getReasonPhrase() : "Error");
        body.put("message", message);
        return body;
    }
}
