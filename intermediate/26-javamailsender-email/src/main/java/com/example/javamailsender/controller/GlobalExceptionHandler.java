package com.example.javamailsender.controller;

import com.example.javamailsender.dto.EmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralised exception handler for all REST controllers in this application.
 *
 * <p>{@code @RestControllerAdvice} is a meta-annotation combining
 * {@code @ControllerAdvice} and {@code @ResponseBody}. It applies globally
 * to all {@code @RestController} classes and serialises the returned objects
 * to JSON automatically.
 *
 * <p>Exceptions handled here:
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} – Bean Validation failures
 *       (e.g., missing required field, invalid email format). Returns 400.</li>
 *   <li>{@link MailException} – Spring Mail send failures (e.g., SMTP server
 *       unreachable, authentication error). Returns 502.</li>
 *   <li>{@link RuntimeException} – Catch-all for unexpected failures,
 *       including wrapped {@link jakarta.mail.MessagingException}. Returns 500.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles Bean Validation failures triggered by {@code @Valid} on controller
     * method parameters.
     *
     * <p>{@link MethodArgumentNotValidException} is thrown by Spring MVC when a
     * request body field fails a constraint (e.g., {@code @NotBlank}, {@code @Email}).
     *
     * <p>All field-level error messages are collected and joined with a separator
     * so the client sees exactly which fields are invalid.
     *
     * @param ex the validation exception containing one or more field errors.
     * @return 400 Bad Request with a descriptive {@link EmailResponse}.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<EmailResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Collect all field-level constraint violation messages into a single string
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", errors);

        EmailResponse response = new EmailResponse("failed", null, "Validation error: " + errors);
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles SMTP / mail send failures thrown by Spring's mail infrastructure.
     *
     * <p>{@link MailException} is the base unchecked exception for all Spring Mail
     * errors. Common subclasses include:
     * <ul>
     *   <li>{@code MailSendException} – SMTP server rejected the message.</li>
     *   <li>{@code MailConnectException} – could not connect to the SMTP server.</li>
     *   <li>{@code MailAuthenticationException} – SMTP authentication failed.</li>
     * </ul>
     *
     * <p>Returns HTTP 502 (Bad Gateway) because the failure is in the downstream
     * SMTP server, not in the client request.
     *
     * @param ex the mail exception from the SMTP layer.
     * @return 502 Bad Gateway with a descriptive {@link EmailResponse}.
     */
    @ExceptionHandler(MailException.class)
    public ResponseEntity<EmailResponse> handleMailException(MailException ex) {
        log.error("Mail send failure: {}", ex.getMessage(), ex);

        EmailResponse response = new EmailResponse(
                "failed",
                null,
                "Email could not be sent: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    /**
     * Catch-all handler for unexpected runtime errors, including wrapped
     * {@link jakarta.mail.MessagingException} from the HTML email path.
     *
     * <p>Returns HTTP 500 (Internal Server Error). Avoid leaking internal
     * stack trace details to the client — the cause is only logged server-side.
     *
     * @param ex the unexpected runtime exception.
     * @return 500 Internal Server Error with a generic {@link EmailResponse}.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<EmailResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Unexpected error while processing email request: {}", ex.getMessage(), ex);

        EmailResponse response = new EmailResponse(
                "failed",
                null,
                "An unexpected error occurred: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
