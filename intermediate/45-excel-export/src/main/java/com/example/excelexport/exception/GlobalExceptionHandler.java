package com.example.excelexport.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST controllers.
 *
 * <p>Uses {@link RestControllerAdvice} (a specialization of {@code @ControllerAdvice}
 * combined with {@code @ResponseBody}) so that every handler method automatically
 * serializes its return value as JSON.
 *
 * <p>Returns RFC 9457 {@link ProblemDetail} responses – Spring 6 / Boot 3 ships
 * with built-in support for this standard error format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation failures triggered by {@code @Valid} on request bodies.
     *
     * <p>{@link MethodArgumentNotValidException} is thrown when the request body
     * fails validation (e.g. a required field is blank or a numeric constraint is
     * violated). We collect all field error messages into a single, readable string
     * and return a 400 Bad Request with a {@link ProblemDetail} body.
     *
     * @param ex the validation exception carrying the list of field errors
     * @return a {@link ProblemDetail} with status 400 and a descriptive message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // Collect all field-level error messages separated by "; "
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        // ProblemDetail is the RFC 9457 standard error response format.
        // It contains: type URI, title, status, detail, and instance URI.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, details);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://example.com/errors/validation-failed"));
        return problem;
    }

    /**
     * Catch-all handler for unexpected exceptions.
     *
     * <p>Returns a generic 500 Internal Server Error response without leaking
     * internal implementation details to the client.
     *
     * @param ex any unhandled exception
     * @return a {@link ProblemDetail} with status 500
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://example.com/errors/internal-server-error"));
        return problem;
    }
}
