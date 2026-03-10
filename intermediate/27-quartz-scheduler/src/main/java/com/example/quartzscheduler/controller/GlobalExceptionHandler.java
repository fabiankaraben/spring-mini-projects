package com.example.quartzscheduler.controller;

import com.example.quartzscheduler.dto.ApiResponse;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralised exception handler for all REST controllers.
 *
 * <p>{@link RestControllerAdvice} is a meta-annotation that combines
 * {@code @ControllerAdvice} (intercepts all controllers) and
 * {@code @ResponseBody} (serialises the return value to JSON).
 *
 * <p>By centralising exception handling here, the controllers remain clean
 * and focused on business logic rather than error-response construction.
 *
 * <h2>Handled exceptions</h2>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} – Bean Validation failures
 *       (e.g. blank jobName, invalid cron pattern).  Mapped to {@code 400}.</li>
 *   <li>{@link IllegalArgumentException} – business-logic validation errors
 *       (e.g. unknown job type, job not found).  Mapped to {@code 400 / 404}.</li>
 *   <li>{@link SchedulerException} – Quartz internal errors.  Mapped to {@code 500}.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles Bean Validation failures triggered by {@code @Valid} on request bodies.
     *
     * <p>Collects all field-level violation messages into a single comma-separated
     * string so the client can see every problem at once rather than fixing one
     * error at a time.
     *
     * @param ex the validation exception thrown by Spring MVC
     * @return HTTP 400 with a structured {@link ApiResponse}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Collect all field error messages into one readable string
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed: " + message));
    }

    /**
     * Handles business-rule violations such as:
     * <ul>
     *   <li>Invalid cron expression ({@link org.quartz.CronExpression})</li>
     *   <li>Unknown job type discriminator</li>
     *   <li>Job not found (mapped to 404)</li>
     * </ul>
     *
     * <p>The "Job not found" case is identified by the message prefix so it can
     * be returned as {@code 404 Not Found} rather than {@code 400 Bad Request}.
     *
     * @param ex the exception
     * @return HTTP 404 if the job does not exist, HTTP 400 otherwise
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        // Return 404 when the job simply does not exist in the scheduler
        if (ex.getMessage() != null && ex.getMessage().startsWith("Job not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }

        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles unexpected Quartz scheduler errors.
     *
     * <p>These are internal infrastructure failures (e.g. database unavailable,
     * cluster communication error) that the client cannot fix; hence {@code 500}.
     *
     * @param ex the Quartz scheduler exception
     * @return HTTP 500 with a generic error message
     */
    @ExceptionHandler(SchedulerException.class)
    public ResponseEntity<ApiResponse> handleSchedulerException(SchedulerException ex) {
        log.error("Scheduler error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Scheduler error: " + ex.getMessage()));
    }
}
