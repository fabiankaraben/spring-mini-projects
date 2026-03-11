package com.example.validationgroups.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for the entire REST API.
 *
 * <p>{@code @RestControllerAdvice} is a meta-annotation that combines
 * {@code @ControllerAdvice} and {@code @ResponseBody}: every method annotated
 * with {@code @ExceptionHandler} here intercepts exceptions from <em>all</em>
 * controllers and serialises the return value as JSON.</p>
 *
 * <h2>Handled exceptions</h2>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} – Bean Validation failures from
 *       {@code @Validated} → HTTP 400 with a map of field errors.</li>
 *   <li>{@link UserNotFoundException} – entity lookup failure → HTTP 404.</li>
 *   <li>{@link EmailAlreadyExistsException} – duplicate email → HTTP 409 Conflict.</li>
 *   <li>{@link PasswordMismatchException} – password confirmation mismatch → HTTP 400.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation failures triggered by {@code @Validated} in controllers.
     *
     * <p>Spring throws {@link MethodArgumentNotValidException} when a request body
     * fails validation.  This handler collects all field-level error messages into a
     * map ({@code fieldName → errorMessage}) and returns it as the response body
     * alongside HTTP 400 Bad Request.</p>
     *
     * <p>This is the primary handler that demonstrates validation groups working:
     * only constraints belonging to the active group produce entries in this map.</p>
     *
     * @param ex the validation exception thrown by Spring MVC
     * @return a structured error body with HTTP 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        // Collect all field errors into fieldName → message pairs.
        // If a field has multiple violations, only the last message is kept
        // (toMap merge function: keep the newer value).
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (existing, newer) -> existing   // keep first message on duplicates
                ));

        // Build the response body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Returns HTTP 404 when a user cannot be found.
     *
     * @param ex the exception containing the missing user ID
     * @return a structured error body with HTTP 404
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Returns HTTP 409 when an email address is already registered.
     *
     * @param ex the exception containing the conflicting email
     * @return a structured error body with HTTP 409 Conflict
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailConflict(EmailAlreadyExistsException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Returns HTTP 400 when the new password and confirmation do not match.
     *
     * @param ex the mismatch exception
     * @return a structured error body with HTTP 400
     */
    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<Map<String, Object>> handlePasswordMismatch(PasswordMismatchException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }
}
