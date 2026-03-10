package com.example.elasticsearchcrud.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all REST controllers.
 *
 * <p>{@link RestControllerAdvice} is a specialisation of {@link org.springframework.stereotype.Component}
 * combined with {@link org.springframework.web.bind.annotation.ResponseBody}. It intercepts
 * exceptions thrown by any {@code @RestController} and returns a structured HTTP error response
 * instead of the default Spring Boot error page.
 *
 * <p>We use {@link ProblemDetail} (RFC 9457 / RFC 7807 "Problem Details for HTTP APIs") as the
 * response body. Spring Boot 3.x supports this natively and serialises it to JSON automatically.
 * A {@code ProblemDetail} body looks like:
 * <pre>{@code
 * {
 *   "type":   "about:blank",
 *   "title":  "Not Found",
 *   "status": 404,
 *   "detail": "Article not found with id: abc123"
 * }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle {@link ArticleNotFoundException} → HTTP 404 Not Found.
     *
     * <p>Thrown by the service layer when {@link com.example.elasticsearchcrud.repository.ArticleRepository}
     * returns no document for the requested ID.
     *
     * @param ex the exception carrying the missing article ID
     * @return a {@link ProblemDetail} response body with status 404
     */
    @ExceptionHandler(ArticleNotFoundException.class)
    public ProblemDetail handleArticleNotFound(ArticleNotFoundException ex) {
        // ProblemDetail.forStatusAndDetail sets the "status" and "detail" fields
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    /**
     * Handle Bean Validation failures → HTTP 400 Bad Request.
     *
     * <p>Spring MVC throws {@link MethodArgumentNotValidException} when a request body
     * annotated with {@code @Valid} fails validation (e.g. a blank title, or a negative
     * view count). We collect all constraint violation messages into a single comma-separated
     * string for a useful error response.
     *
     * @param ex the validation exception containing all field errors
     * @return a {@link ProblemDetail} response body with status 400 and all error messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        // Extract all field error messages and join them into a readable string
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("about:blank"));
        return problem;
    }
}
