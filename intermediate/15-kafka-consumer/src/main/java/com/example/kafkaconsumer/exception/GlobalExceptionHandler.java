package com.example.kafkaconsumer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised error-handling advice for all REST controllers.
 *
 * <p>{@code @RestControllerAdvice} is a specialisation of {@code @ControllerAdvice}
 * that applies {@code @ResponseBody} to every {@code @ExceptionHandler} method,
 * so the returned objects are automatically serialised to JSON.
 *
 * <p>Centralising exception handling here keeps the controllers clean:
 * controllers never need {@code try/catch} blocks for predictable error cases.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles unexpected runtime errors propagating out of controller methods.
     *
     * <p>Any {@link RuntimeException} that is not caught elsewhere is mapped to
     * a {@code 500 Internal Server Error} response. The exception message is
     * included to aid debugging; a production system should mask sensitive
     * details and log the full stack trace separately.
     *
     * @param ex the runtime exception
     * @return a {@code 500} response with a short error description and timestamp
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", ex.getMessage());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
