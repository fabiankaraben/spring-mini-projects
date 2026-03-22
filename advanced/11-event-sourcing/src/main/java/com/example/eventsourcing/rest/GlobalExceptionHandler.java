package com.example.eventsourcing.rest;

import com.example.eventsourcing.command.aggregate.InsufficientFundsException;
import org.axonframework.commandhandling.CommandExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler that translates domain exceptions into appropriate HTTP responses.
 *
 * <h2>Why this is needed</h2>
 * When an aggregate's {@code @CommandHandler} throws an exception, Axon wraps it in a
 * {@link CommandExecutionException} before propagating it back to the caller. This handler
 * unwraps the cause and maps it to the correct HTTP status code.
 *
 * <h2>Exception → HTTP status mapping</h2>
 * <ul>
 *   <li>{@link InsufficientFundsException} → 409 Conflict</li>
 *   <li>{@link IllegalStateException} (e.g. "Account is closed") → 409 Conflict</li>
 *   <li>{@link IllegalArgumentException} (e.g. "Negative amount") → 400 Bad Request</li>
 *   <li>{@link MethodArgumentNotValidException} (Bean Validation) → 400 Bad Request</li>
 *   <li>Any other exception → 500 Internal Server Error</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Axon's {@link CommandExecutionException} — unwraps the root cause and
     * delegates to the appropriate specific handler.
     *
     * @param ex the Axon command execution exception
     * @return the appropriate error response
     */
    @ExceptionHandler(CommandExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleCommandExecutionException(
            CommandExecutionException ex) {

        // Unwrap the original domain exception thrown inside the aggregate
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

        if (cause instanceof InsufficientFundsException) {
            return errorResponse(HttpStatus.CONFLICT, cause.getMessage());
        }
        if (cause instanceof IllegalStateException) {
            return errorResponse(HttpStatus.CONFLICT, cause.getMessage());
        }
        if (cause instanceof IllegalArgumentException) {
            return errorResponse(HttpStatus.BAD_REQUEST, cause.getMessage());
        }

        // Fallback for unexpected errors
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /**
     * Handles {@link InsufficientFundsException} thrown directly (not wrapped by Axon).
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientFunds(
            InsufficientFundsException ex) {
        return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles {@link IllegalStateException} thrown directly (e.g. "Account is already closed").
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles {@link IllegalArgumentException} thrown directly (e.g. "Negative amount").
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Handles Bean Validation failures ({@link MethodArgumentNotValidException}).
     * Collects all field-level validation errors into a single error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        // Collect all validation error messages
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return errorResponse(HttpStatus.BAD_REQUEST, message);
    }

    // =========================================================================
    //  Helper
    // =========================================================================

    /**
     * Builds a consistent error response body with status, message, and timestamp.
     *
     * @param status  the HTTP status code
     * @param message a human-readable error description
     * @return the response entity
     */
    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }
}
