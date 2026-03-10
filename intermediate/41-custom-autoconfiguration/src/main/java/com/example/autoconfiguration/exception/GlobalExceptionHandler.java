package com.example.autoconfiguration.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * Centralised exception handler for the REST API.
 *
 * <p>{@code @RestControllerAdvice} combines {@code @ControllerAdvice} (applies to all
 * controllers) with {@code @ResponseBody} (serialises the return value to JSON).
 * Each {@code @ExceptionHandler} method maps a specific exception type to an HTTP
 * response with an appropriate status code and body.
 *
 * <p>Uses RFC 7807 {@link ProblemDetail} (introduced in Spring 6 / Spring Boot 3)
 * for a standardised JSON error format:
 * <pre>{@code
 * {
 *   "type": "https://example.com/problems/not-found",
 *   "title": "Not Found",
 *   "status": 404,
 *   "detail": "Greeting log not found with id: 99",
 *   "timestamp": "2024-01-01T10:00:00Z"
 * }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@link GreetingLogNotFoundException} by returning HTTP 404.
     *
     * @param ex the exception thrown by the service layer
     * @return a {@link ProblemDetail} with status 404 and the exception message
     */
    @ExceptionHandler(GreetingLogNotFoundException.class)
    public ProblemDetail handleGreetingLogNotFound(GreetingLogNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        problem.setType(URI.create("https://example.com/problems/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Handles {@link MethodArgumentNotValidException} (Bean Validation failures)
     * by returning HTTP 400 with details about which fields failed validation.
     *
     * @param ex the validation exception thrown by Spring MVC
     * @return a {@link ProblemDetail} with status 400 and a list of field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        // Collect all field error messages into one string for the detail field
        String detail = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://example.com/problems/validation-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
