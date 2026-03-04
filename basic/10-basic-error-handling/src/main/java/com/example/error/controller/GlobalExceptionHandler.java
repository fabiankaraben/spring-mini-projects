package com.example.error.controller;

import com.example.error.exception.InvalidProductException;
import com.example.error.exception.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler using @RestControllerAdvice.
 * <p>
 * This class captures specific exceptions thrown by any controller and
 * translates
 * them into standard RFC 7807 problem details responses over HTTP.
 * Implementing Spring Boot 3's ProblemDetail is a clean way to format standard
 * errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles ProductNotFoundException.
     * Maps to HTTP 404 Not Found.
     *
     * @param ex the exception
     * @return ProblemDetail containing standard RFC 7807 fields
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFoundException(ProductNotFoundException ex) {
        // Create ProblemDetail with HTTP 404
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());

        // Add custom properties and URI type according to RFC 7807
        problemDetail.setTitle("Product Not Found");
        problemDetail.setType(URI.create("https://example.com/problems/product-not-found"));
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }

    /**
     * Handles InvalidProductException.
     * Maps to HTTP 400 Bad Request.
     *
     * @param ex the exception
     * @return ProblemDetail containing standard RFC 7807 fields
     */
    @ExceptionHandler(InvalidProductException.class)
    public ProblemDetail handleInvalidProductException(InvalidProductException ex) {
        // Create ProblemDetail with HTTP 400
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());

        // Add custom properties and URI type according to RFC 7807
        problemDetail.setTitle("Invalid Product Details");
        problemDetail.setType(URI.create("https://example.com/problems/invalid-product"));
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }
}
