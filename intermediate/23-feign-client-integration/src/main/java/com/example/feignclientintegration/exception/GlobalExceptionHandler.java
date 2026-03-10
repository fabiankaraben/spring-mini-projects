package com.example.feignclientintegration.exception;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Feign Client Integration application.
 *
 * <p>{@link RestControllerAdvice} is a specialisation of {@link org.springframework.web.bind.annotation.ControllerAdvice}
 * that adds {@link org.springframework.web.bind.annotation.ResponseBody} to every
 * {@link ExceptionHandler} method, so all error responses are serialised to JSON
 * automatically.
 *
 * <p>This handler deals with two main error categories:
 * <ol>
 *   <li><strong>Feign errors</strong> — the upstream API (JSONPlaceholder) returned
 *       a non-2xx status. Feign wraps these in {@link FeignException} subclasses.</li>
 *   <li><strong>Validation errors</strong> — the incoming request body failed
 *       {@code @Valid} / Bean Validation constraints.</li>
 * </ol>
 *
 * <p>All responses use RFC 9457 {@link ProblemDetail} (introduced in Spring 6),
 * which is the standard JSON error format:
 * <pre>
 * {
 *   "type":   "https://problems.example.com/feign-not-found",
 *   "title":  "Upstream Resource Not Found",
 *   "status": 404,
 *   "detail": "The upstream API returned 404 for: GET /posts/999"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle upstream 404 responses from the Feign client.
     *
     * <p>When the JSONPlaceholder API returns HTTP 404, Feign throws a
     * {@link FeignException.NotFound}. We catch it here and re-map it to our
     * own 404 response so the caller receives a structured JSON error body
     * rather than an unformatted Feign exception message.
     *
     * @param ex the Feign 404 exception
     * @return 404 Not Found with ProblemDetail body
     */
    @ExceptionHandler(FeignException.NotFound.class)
    public ProblemDetail handleFeignNotFound(FeignException.NotFound ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "The upstream API returned 404: " + ex.getMessage()
        );
        problem.setTitle("Upstream Resource Not Found");
        problem.setType(URI.create("https://problems.example.com/feign-not-found"));
        return problem;
    }

    /**
     * Handle upstream 5xx responses from the Feign client.
     *
     * <p>Feign wraps 5xx upstream errors in {@link FeignException.InternalServerError}
     * and other server-error subclasses. We re-map these to 502 Bad Gateway,
     * which semantically means "the server received an invalid response from
     * the upstream server it was acting as a gateway for."
     *
     * @param ex the Feign server-side exception
     * @return 502 Bad Gateway with ProblemDetail body
     */
    @ExceptionHandler(FeignException.class)
    public ProblemDetail handleFeignException(FeignException ex) {
        // If it's a 4xx client error from upstream, propagate as 400
        if (ex.status() >= 400 && ex.status() < 500) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.valueOf(ex.status()),
                    "Upstream API client error: " + ex.getMessage()
            );
            problem.setTitle("Upstream Client Error");
            problem.setType(URI.create("https://problems.example.com/feign-client-error"));
            return problem;
        }
        // For 5xx and other errors, respond with 502 Bad Gateway
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "The upstream API returned an error: " + ex.getMessage()
        );
        problem.setTitle("Upstream API Error");
        problem.setType(URI.create("https://problems.example.com/feign-upstream-error"));
        return problem;
    }

    /**
     * Handle {@code @Valid} Bean Validation failures on request bodies.
     *
     * <p>Spring MVC throws {@link MethodArgumentNotValidException} when an
     * {@code @RequestBody} annotated with {@code @Valid} fails validation.
     * We collect all field error messages and return them as a single
     * structured 400 Bad Request response.
     *
     * @param ex the validation exception containing field error details
     * @return 400 Bad Request with ProblemDetail body listing all violations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        // Collect all field-level validation error messages into one string
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                detail
        );
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://problems.example.com/validation-error"));
        return problem;
    }
}
