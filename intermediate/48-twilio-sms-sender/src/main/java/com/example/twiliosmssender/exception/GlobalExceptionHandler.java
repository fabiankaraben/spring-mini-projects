package com.example.twiliosmssender.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Twilio SMS Sender REST API.
 *
 * <p>{@code @RestControllerAdvice} makes this class intercept exceptions thrown by
 * any {@code @RestController} and convert them into structured HTTP error responses.
 *
 * <p>Uses RFC 7807 "Problem Details for HTTP APIs" via Spring's {@link ProblemDetail}
 * so that error responses have a consistent, machine-readable JSON structure:
 * <pre>{@code
 * {
 *   "type":   "https://example.com/errors/not-found",
 *   "title":  "Not Found",
 *   "status": 404,
 *   "detail": "SMS message not found with id: 99",
 *   "instance": null
 * }
 * }</pre>
 *
 * <p>Exceptions handled:
 * <ul>
 *   <li>{@link SmsNotFoundException}          → HTTP 404 Not Found</li>
 *   <li>{@link TwilioSmsException}             → HTTP 502 Bad Gateway</li>
 *   <li>{@link MethodArgumentNotValidException} → HTTP 400 Bad Request</li>
 *   <li>{@link Exception} (catch-all)          → HTTP 500 Internal Server Error</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ─────────────────────────────────────────────────────────────────────────
    // 404 Not Found
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles {@link SmsNotFoundException} thrown when a message record is not found.
     *
     * @param ex the exception containing the missing ID or SID
     * @return HTTP 404 with a ProblemDetail body
     */
    @ExceptionHandler(SmsNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleSmsNotFound(SmsNotFoundException ex) {
        log.warn("SMS message not found: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("SMS Message Not Found");
        problem.setType(URI.create("https://example.com/errors/not-found"));
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 502 Bad Gateway (Twilio API error)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles {@link TwilioSmsException} thrown when the Twilio API returns an error.
     *
     * <p>HTTP 502 Bad Gateway is appropriate here because the application itself is
     * functioning correctly, but an upstream service (Twilio) returned an error.
     *
     * @param ex the exception wrapping the Twilio API failure
     * @return HTTP 502 with a ProblemDetail body
     */
    @ExceptionHandler(TwilioSmsException.class)
    public ResponseEntity<ProblemDetail> handleTwilioError(TwilioSmsException ex) {
        log.error("Twilio API error: {}", ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Twilio API Error");
        problem.setType(URI.create("https://example.com/errors/twilio-error"));
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 400 Bad Request (Bean Validation)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles {@link MethodArgumentNotValidException} thrown when request body
     * validation fails (e.g., missing required fields, invalid phone number format).
     *
     * <p>Collects all validation errors into a single human-readable detail string.
     *
     * @param ex the validation exception containing all field errors
     * @return HTTP 400 with a ProblemDetail body listing all validation failures
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Collect all field validation errors into a comma-separated string
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", detail);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://example.com/errors/validation-error"));
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 500 Internal Server Error (catch-all)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Catch-all handler for unexpected exceptions not handled by other methods.
     *
     * <p>Logs the full stack trace but returns only a generic message to the client
     * to avoid leaking internal implementation details.
     *
     * @param ex the unexpected exception
     * @return HTTP 500 with a generic ProblemDetail body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericError(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://example.com/errors/internal-error"));
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
