package com.example.entitylifecycle.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST controllers.
 *
 * <p>{@code @RestControllerAdvice} is a meta-annotation combining
 * {@code @ControllerAdvice} and {@code @ResponseBody}. Every
 * {@code @ExceptionHandler} method here applies globally to all controllers in
 * this application context and returns its result as JSON (not a view).
 *
 * <p>Responses use the RFC 7807 {@link ProblemDetail} format introduced in
 * Spring Framework 6 / Spring Boot 3. This produces a standardized error body:
 * <pre>{@code
 * {
 *   "type":   "about:blank",
 *   "title":  "Not Found",
 *   "status": 404,
 *   "detail": "Product not found with id: 99"
 * }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@link ProductNotFoundException} thrown from the service layer when
     * a requested product ID does not exist in the database.
     *
     * @param ex the exception carrying the missing-ID message
     * @return a 404 Not Found ProblemDetail response
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleNotFound(ProductNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * Handles Bean Validation failures triggered when a {@code @Valid}-annotated
     * controller parameter fails validation (e.g. blank name, negative price).
     *
     * <p>Collects all field-level validation errors into a map of
     * {@code fieldName -> errorMessage} pairs and attaches it to the problem detail.
     *
     * @param ex the validation exception containing all constraint violations
     * @return a 400 Bad Request ProblemDetail response with per-field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // Collect all field errors into a field-name -> message map
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid value",
                        // If the same field has multiple violations, keep the last message
                        (existing, replacement) -> replacement
                ));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setDetail("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
