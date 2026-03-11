package com.example.stripepayment.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers in this application.
 *
 * <p>{@link RestControllerAdvice} makes this class applicable to every
 * {@code @RestController} without any per-controller wiring.
 *
 * <p>This handler uses {@link ProblemDetail} (RFC 7807 – Problem Details for
 * HTTP APIs), which is natively supported by Spring Boot 3.x. The response
 * body includes {@code type}, {@code title}, {@code status}, and {@code detail}
 * fields for consistent error reporting.
 *
 * <p>Exceptions handled:
 * <ul>
 *   <li>{@link PaymentNotFoundException}   → 404 Not Found</li>
 *   <li>{@link StripePaymentException}     → 502 Bad Gateway (upstream Stripe error)</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (Bean Validation)</li>
 *   <li>{@link Exception}                  → 500 Internal Server Error (catch-all)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ─────────────────────────────────────────────────────────────────────────
    // 404 Not Found
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles cases where a payment record cannot be found.
     *
     * @param ex the exception thrown by the service layer
     * @return a 404 ProblemDetail response
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException ex) {
        log.warn("Payment not found: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Payment Not Found");
        problem.setType(URI.create("/errors/payment-not-found"));
        return problem;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 502 Bad Gateway – Stripe API errors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles failures when communicating with the Stripe API.
     *
     * <p>A 502 response signals to the client that our server received an invalid
     * response from an upstream service (Stripe in this case).
     *
     * @param ex the exception thrown when the Stripe SDK fails
     * @return a 502 ProblemDetail response
     */
    @ExceptionHandler(StripePaymentException.class)
    public ProblemDetail handleStripeException(StripePaymentException ex) {
        log.error("Stripe API error: {}", ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Stripe API Error");
        problem.setType(URI.create("/errors/stripe-api-error"));
        return problem;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 400 Bad Request – Bean Validation failures
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles Bean Validation (@Valid) failures on request bodies and parameters.
     *
     * <p>The response includes a map of field names to their validation error
     * messages to help the client understand exactly what went wrong.
     *
     * @param ex the validation exception containing field errors
     * @return a 400 ProblemDetail response with field-level error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        // Collect all field errors into a Map<fieldName, errorMessage>
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Invalid value",
                        // Keep the first message if a field has multiple violations
                        (first, second) -> first
                ));

        log.warn("Validation failed: {}", fieldErrors);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation Error");
        problem.setType(URI.create("/errors/validation-error"));
        // Add the field errors as a custom property in the response body
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 500 Internal Server Error – catch-all
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Catch-all handler for any unexpected exception not covered by the above handlers.
     *
     * @param ex the unhandled exception
     * @return a 500 ProblemDetail response
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("/errors/internal-server-error"));
        return problem;
    }
}
