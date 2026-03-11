package com.example.testcontainerspostgres.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception handler for the REST API.
 *
 * <p>{@code @RestControllerAdvice} is a combination of {@code @ControllerAdvice}
 * and {@code @ResponseBody}. It intercepts exceptions thrown by any
 * {@code @RestController} and converts them into JSON HTTP responses,
 * so controller methods stay clean and free of try/catch blocks.
 *
 * <p>Spring Boot 3 uses the RFC 7807 "Problem Details" standard via
 * {@link ProblemDetail}, which produces a structured JSON body like:
 * <pre>
 * {
 *   "type": "about:blank",
 *   "title": "Not Found",
 *   "status": 404,
 *   "detail": "Product not found with id: 99"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@link ProductNotFoundException} and returns HTTP 404.
     *
     * <p>{@link ProblemDetail#forStatusAndDetail} is the idiomatic Spring Boot 3
     * way to build a Problem Details response. It sets the HTTP status, the
     * human-readable detail message, and appropriate headers automatically.
     *
     * @param ex the exception carrying the "not found" message
     * @return a 404 Problem Details response body
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles Bean Validation failures ({@code @Valid} violations) and returns HTTP 400.
     *
     * <p>When Spring MVC's {@code @Valid} annotation rejects a request body,
     * it throws {@link MethodArgumentNotValidException}. This handler collects
     * every field error into a map {@code {fieldName: errorMessage}} and
     * attaches it to the Problem Details response so clients know exactly
     * which fields were invalid.
     *
     * @param ex the validation exception containing all field errors
     * @return a 400 Problem Details response with a {@code "errors"} property
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        // Collect all field-level validation errors into a map for easy reading
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );
        // Attach the field-error map as an extra property in the response body
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }
}
