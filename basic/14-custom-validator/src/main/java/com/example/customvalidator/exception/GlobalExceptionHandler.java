package com.example.customvalidator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler to capture validation errors and map them to
 * standard RFC 7807 problem details.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles specific exception thrown when bean validation fails.
     * 
     * @param ex The MethodArgumentNotValidException exception handled by @Valid.
     * @return Standardized ProblemDetail with specific validation errors in
     *         properties.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {

        // Use RFC 7807 problem detail specification feature from Spring Boot 3
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Validation failed for request.");

        problemDetail.setTitle("Bad Request");

        try {
            problemDetail.setType(new URI("https://example.com/probs/validation-error"));
        } catch (Exception ignored) {
            // Ignore URI exception
        }

        // Collect all field errors occurred and their messages
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        // Add them to standard properties extension
        problemDetail.setProperty("invalid-params", errors);

        return problemDetail;
    }
}
