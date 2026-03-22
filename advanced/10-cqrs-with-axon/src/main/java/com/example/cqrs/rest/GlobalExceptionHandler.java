package com.example.cqrs.rest;

import org.axonframework.commandhandling.CommandExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Global exception handler for the REST layer.
 *
 * <p>Translates domain and infrastructure exceptions into appropriate HTTP responses
 * using the RFC 7807 "Problem Details" format ({@link ProblemDetail}).
 *
 * <p>Without this handler, Axon wraps domain exceptions in a
 * {@link CommandExecutionException}, which Spring would return as a generic 500.
 * This handler unwraps the cause and maps it to the correct HTTP status code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles Axon's {@link CommandExecutionException} — thrown when a command handler
     * (i.e. the aggregate) throws a checked or unchecked exception.
     *
     * <p>We inspect the cause to decide the HTTP status:
     * <ul>
     *   <li>{@link IllegalStateException} → 409 Conflict (e.g. wrong order status)</li>
     *   <li>{@link IllegalArgumentException} → 400 Bad Request (e.g. invalid quantity)</li>
     *   <li>Anything else → 500 Internal Server Error</li>
     * </ul>
     *
     * @param ex the Axon wrapper exception
     * @return a structured RFC 7807 problem detail response
     */
    @ExceptionHandler(CommandExecutionException.class)
    public ResponseEntity<ProblemDetail> handleCommandExecutionException(CommandExecutionException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        log.warn("Command execution failed: {}", cause.getMessage());

        if (cause instanceof IllegalStateException) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, cause.getMessage());
            problem.setType(URI.create("/errors/conflict"));
            problem.setTitle("Order state conflict");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }

        if (cause instanceof IllegalArgumentException) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, cause.getMessage());
            problem.setType(URI.create("/errors/bad-request"));
            problem.setTitle("Invalid command");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
        }

        // Fallback for unexpected command failures
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(URI.create("/errors/internal"));
        problem.setTitle("Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    /**
     * Handles {@link IllegalStateException} thrown directly by command handlers
     * (e.g. when Axon propagates the cause without wrapping in CommandExecutionException).
     * Maps to 409 Conflict.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalStateException(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("/errors/conflict"));
        problem.setTitle("Order state conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Handles {@link IllegalArgumentException} thrown directly by command handlers.
     * Maps to 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("/errors/bad-request"));
        problem.setTitle("Invalid command");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handles Bean Validation failures ({@code @Valid} on request bodies).
     * Returns 400 with a summary of all violated constraints.
     *
     * @param ex the validation exception from Spring MVC
     * @return 400 Bad Request with field-level error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex) {
        // Collect all field errors into a readable message
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", detail);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("/errors/validation"));
        problem.setTitle("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
