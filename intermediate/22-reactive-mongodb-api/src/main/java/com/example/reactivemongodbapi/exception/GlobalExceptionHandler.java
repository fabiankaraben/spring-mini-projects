package com.example.reactivemongodbapi.exception;

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
import java.util.stream.Collectors;

/**
 * Global exception handler for the reactive REST API.
 *
 * <p>{@link RestControllerAdvice} is a combination of {@code @ControllerAdvice} and
 * {@code @ResponseBody}. It intercepts exceptions thrown anywhere in the controller or
 * service layer and converts them into structured JSON error responses.
 *
 * <p><strong>Why a global handler?</strong>
 * <ul>
 *   <li>Keeps exception-handling logic out of individual controllers (DRY principle).</li>
 *   <li>Produces consistent, structured error responses across all endpoints.</li>
 *   <li>Avoids leaking internal stack traces to API clients.</li>
 * </ul>
 *
 * <p><strong>Reactive note:</strong><br>
 * In Spring WebFlux, exception handlers return {@link Mono} or {@link ResponseEntity}
 * wrapping a {@link Mono}. The framework subscribes to the returned publisher and
 * writes the response body asynchronously — matching the non-blocking execution model.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle {@link WebExchangeBindException} — thrown by Spring WebFlux when
     * {@code @Valid} validation fails on a request body or query parameter.
     *
     * <p>Collects all field-level validation errors into a list of
     * {@code "field: message"} strings for a developer-friendly 400 response.
     *
     * @param ex the binding exception containing all constraint violations
     * @return a Mono emitting a 400 Bad Request response with field error details
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationException(
            WebExchangeBindException ex) {

        // Collect each field violation into a human-readable "field: message" string
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());

        Map<String, Object> body = buildErrorBody(HttpStatus.BAD_REQUEST, "Validation failed", errors);
        return Mono.just(ResponseEntity.badRequest().body(body));
    }

    /**
     * Handle {@link IllegalStateException} — used in the service layer to signal
     * business rule violations such as duplicate ISBN.
     *
     * <p>Maps to HTTP 409 Conflict because the request is valid (well-formed JSON,
     * passes bean validation) but conflicts with existing data in MongoDB.
     *
     * @param ex the exception describing the conflict
     * @return a Mono emitting a 409 Conflict response
     */
    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleConflict(IllegalStateException ex) {
        Map<String, Object> body = buildErrorBody(HttpStatus.CONFLICT, ex.getMessage(), List.of());
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(body));
    }

    /**
     * Handle {@link ResponseStatusException} — thrown explicitly by controllers
     * (e.g., {@code Mono.error(new ResponseStatusException(NOT_FOUND, ...))}).
     *
     * <p>Forwards the HTTP status from the exception to the response, preserving
     * the intent of the throwing code (404 stays 404, etc.).
     *
     * @param ex the response status exception
     * @return a Mono emitting a response with the exception's HTTP status
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleResponseStatus(
            ResponseStatusException ex) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        Map<String, Object> body = buildErrorBody(status, ex.getReason(), List.of());
        return Mono.just(ResponseEntity.status(status).body(body));
    }

    /**
     * Catch-all handler for any unhandled {@link Exception}.
     *
     * <p>Returns a generic HTTP 500 Internal Server Error without leaking internal
     * details to the client. The full stack trace is logged by Spring's default
     * error handling mechanism.
     *
     * @param ex the unhandled exception
     * @return a Mono emitting a 500 Internal Server Error response
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGeneric(Exception ex) {
        Map<String, Object> body = buildErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", List.of());
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body));
    }

    /**
     * Build a consistent error response body map.
     *
     * <p>All error responses share this structure:
     * <pre>{@code
     * {
     *   "timestamp": "2024-01-01T12:00:00Z",
     *   "status": 400,
     *   "error": "Bad Request",
     *   "message": "Validation failed",
     *   "errors": ["title: must not be blank"]
     * }
     * }</pre>
     *
     * @param status  the HTTP status for this error
     * @param message a human-readable description of the error
     * @param errors  a list of specific error details (can be empty)
     * @return a map suitable for Jackson JSON serialisation
     */
    private Map<String, Object> buildErrorBody(HttpStatus status, String message, List<String> errors) {
        // LinkedHashMap preserves insertion order in the JSON output
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (!errors.isEmpty()) {
            body.put("errors", errors);
        }
        return body;
    }
}
