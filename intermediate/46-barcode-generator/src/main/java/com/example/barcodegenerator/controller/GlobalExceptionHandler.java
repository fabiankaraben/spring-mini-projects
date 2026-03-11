package com.example.barcodegenerator.controller;

import com.example.barcodegenerator.exception.BarcodeGenerationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Centralised exception handler for all REST controllers in this application.
 *
 * <p>Spring MVC invokes methods in this class whenever an exception escapes
 * a controller method. Each handler method maps one exception type to an
 * appropriate HTTP status code and a {@link ProblemDetail} response body
 * following RFC 9457 (formerly RFC 7807).
 *
 * <p>Using {@link ProblemDetail} is idiomatic in Spring Boot 3.x; it produces
 * a standardised JSON structure:
 * <pre>{@code
 * {
 *   "type":   "about:blank",
 *   "title":  "Bad Request",
 *   "status": 400,
 *   "detail": "Failed to encode content for format EAN_13: ..."
 * }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles failures from ZXing when the content cannot be encoded in the
     * requested format (e.g. non-numeric data for EAN-13).
     *
     * @param ex the generation exception thrown by {@link com.example.barcodegenerator.service.BarcodeService}
     * @return a 400 Bad Request {@link ProblemDetail} with the error description
     */
    @ExceptionHandler(BarcodeGenerationException.class)
    public ProblemDetail handleBarcodeGenerationException(BarcodeGenerationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Barcode Generation Failed");
        return problem;
    }

    /**
     * Handles missing required query parameters (e.g. {@code content} not provided).
     *
     * @param ex the Spring exception wrapping the missing parameter name
     * @return a 400 Bad Request {@link ProblemDetail} describing which parameter is missing
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing");
        problem.setTitle("Missing Request Parameter");
        return problem;
    }

    /**
     * Handles Bean Validation constraint violations on controller method parameters.
     *
     * <p>Thrown when {@code @Min}/{@code @Max}/{@code @NotBlank} constraints placed
     * directly on {@code @RequestParam} parameters are violated (requires
     * {@code @Validated} on the controller class).
     *
     * @param ex the constraint violation exception containing one or more violations
     * @return a 400 Bad Request {@link ProblemDetail} listing the violated constraints
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        // Collect all violation messages into a single readable string
        String detail = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse(ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        return problem;
    }

    /**
     * Handles type conversion failures, most commonly an unrecognised value for
     * the {@code format} parameter (e.g. {@code format=INVALID}).
     *
     * @param ex the Spring exception wrapping the type mismatch
     * @return a 400 Bad Request {@link ProblemDetail} with the accepted values listed
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'. "
                + "Accepted formats: QR_CODE, CODE_128, EAN_13, UPC_A, CODE_39, PDF_417";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid Parameter Value");
        return problem;
    }
}
