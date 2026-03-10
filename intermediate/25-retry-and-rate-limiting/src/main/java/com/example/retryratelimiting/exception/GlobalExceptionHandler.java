package com.example.retryratelimiting.exception;

import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Global exception handler for the Retry and Rate Limiting application.
 *
 * <p>{@link RestControllerAdvice} makes this class an AOP advice that intercepts
 * exceptions thrown by any {@code @RestController} in the application context.
 * Instead of letting Spring Boot return its default error page, we return a
 * structured {@link ProblemDetail} response (RFC 7807 — Problem Details for HTTP APIs).
 *
 * <p>Handled exceptions:
 * <ul>
 *   <li>{@link MissingServletRequestParameterException} – 400 Bad Request when
 *       the required {@code ?city=} query parameter is missing.</li>
 *   <li>{@link NoSuchElementException} – 404 Not Found when a named Resilience4j
 *       instance (Retry or RateLimiter) does not exist in the registry.</li>
 *   <li>{@link Exception} – 500 Internal Server Error catch-all for unexpected errors.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle missing required query parameters.
     *
     * <p>Returned when the client calls {@code GET /api/weather} without the
     * mandatory {@code ?city=} parameter.
     *
     * @param ex the exception thrown by Spring MVC parameter binding
     * @return 400 Bad Request with a Problem Detail body
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing required request parameter: {}", ex.getParameterName());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("urn:problem:missing-parameter"));
        problem.setTitle("Missing Required Parameter");
        problem.setDetail("Required query parameter '" + ex.getParameterName() + "' is missing.");
        return problem;
    }

    /**
     * Handle requests for non-existent Resilience4j instances.
     *
     * <p>Returned when the client calls the status endpoint with a name that is
     * not registered in the Retry or RateLimiter registry.
     * Resilience4j registries throw {@link NoSuchElementException} when a named
     * instance cannot be found.
     *
     * @param ex the exception thrown by the Resilience4j registry lookup
     * @return 404 Not Found with a Problem Detail body
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleEntryNotFound(NoSuchElementException ex) {
        log.warn("Resilience4j instance not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("urn:problem:resilience4j-instance-not-found"));
        problem.setTitle("Resilience4j Instance Not Found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * Catch-all handler for unexpected exceptions.
     *
     * <p>Returns a generic 500 error instead of exposing internal stack traces.
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error with a Problem Detail body
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("urn:problem:internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please try again later.");
        return problem;
    }
}
