package com.example.circuitbreaker.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;

/**
 * Centralised exception handler for the Circuit Breaker application.
 *
 * <p>{@link RestControllerAdvice} makes this class apply to all {@link org.springframework.web.bind.annotation.RestController}
 * beans. Each {@link ExceptionHandler} method maps an exception type to an
 * appropriate HTTP response.
 *
 * <p>Spring Boot 3 uses {@link ProblemDetail} (RFC 7807) as the standard
 * error response format. Each handler builds a {@link ProblemDetail} with a
 * descriptive title, detail message, and problem type URI.
 *
 * <p>Note: most failures are transparently handled by the circuit breaker's
 * fallback method (returning a degraded response). This handler catches cases
 * where the circuit breaker itself throws or where no fallback is configured.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle {@link CallNotPermittedException} – thrown by Resilience4j when
     * the circuit breaker is OPEN and no fallback method is configured.
     *
     * <p>In this project all methods have fallbacks, so this handler is a safety
     * net for any circuit breaker instance that might be added without a fallback.
     *
     * @param ex the exception thrown by the circuit breaker
     * @return 503 Service Unavailable with a Problem Detail body
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCallNotPermitted(CallNotPermittedException ex) {
        log.warn("Circuit breaker '{}' is OPEN – call rejected", ex.getCausingCircuitBreakerName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Circuit breaker '" + ex.getCausingCircuitBreakerName()
                        + "' is OPEN. The upstream service is temporarily unavailable."
        );
        problem.setTitle("Circuit Breaker Open");
        problem.setType(URI.create("https://example.com/errors/circuit-breaker-open"));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    /**
     * Handle HTTP 4xx errors from the upstream inventory API.
     *
     * <p>{@link HttpClientErrorException} is thrown by {@link org.springframework.web.client.RestTemplate}
     * when the upstream responds with a 4xx status code (e.g., 404 Not Found).
     *
     * @param ex the exception containing the upstream HTTP status
     * @return the same HTTP status as the upstream (propagates 404 → 404, etc.)
     */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ProblemDetail> handleClientError(HttpClientErrorException ex) {
        log.warn("Upstream inventory service returned client error: {}", ex.getStatusCode());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                ex.getStatusCode(),
                "The upstream inventory service returned: " + ex.getMessage()
        );
        problem.setTitle("Upstream Client Error");
        problem.setType(URI.create("https://example.com/errors/upstream-client-error"));
        return ResponseEntity.status(ex.getStatusCode()).body(problem);
    }

    /**
     * Handle HTTP 5xx errors from the upstream inventory API.
     *
     * <p>{@link HttpServerErrorException} is thrown when the upstream responds
     * with a 5xx status (e.g., 500 Internal Server Error, 503 Service Unavailable).
     * These are counted as failures by the circuit breaker.
     *
     * @param ex the exception containing the upstream HTTP status
     * @return 502 Bad Gateway – our service couldn't get a valid response upstream
     */
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ProblemDetail> handleServerError(HttpServerErrorException ex) {
        log.error("Upstream inventory service returned server error: {}", ex.getStatusCode());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "The upstream inventory service failed with: " + ex.getMessage()
        );
        problem.setTitle("Upstream Server Error");
        problem.setType(URI.create("https://example.com/errors/upstream-server-error"));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

    /**
     * Handle connection-level failures such as timeouts or DNS resolution errors.
     *
     * <p>{@link ResourceAccessException} wraps {@link java.io.IOException} and is
     * thrown by {@link org.springframework.web.client.RestTemplate} when a network
     * error occurs (refused connection, read timeout, etc.).
     *
     * @param ex the exception wrapping the underlying I/O error
     * @return 503 Service Unavailable
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ProblemDetail> handleResourceAccess(ResourceAccessException ex) {
        log.error("Could not reach upstream inventory service: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Could not reach the upstream inventory service: " + ex.getMessage()
        );
        problem.setTitle("Upstream Unreachable");
        problem.setType(URI.create("https://example.com/errors/upstream-unreachable"));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
