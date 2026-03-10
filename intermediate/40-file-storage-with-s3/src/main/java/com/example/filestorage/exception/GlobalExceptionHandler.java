package com.example.filestorage.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handler for the REST API.
 *
 * <p>Uses Spring's {@link ProblemDetail} (RFC 7807) format so that all error
 * responses share a consistent JSON structure:
 * <pre>
 * {
 *   "type": "about:blank",
 *   "title": "Not Found",
 *   "status": 404,
 *   "detail": "File not found in bucket: photos/missing.jpg"
 * }
 * </pre>
 *
 * <p>This class is annotated with {@code @RestControllerAdvice} which makes it
 * apply globally to all {@code @RestController} classes without any explicit
 * registration.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@link FileNotFoundException}: maps to HTTP 404 Not Found.
     *
     * @param ex the exception thrown when a file key does not exist in the bucket
     * @return a {@link ProblemDetail} body with 404 status
     */
    @ExceptionHandler(FileNotFoundException.class)
    public ProblemDetail handleFileNotFound(FileNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("File Not Found");
        return detail;
    }

    /**
     * Handles {@link StorageException}: maps to HTTP 500 Internal Server Error.
     *
     * @param ex the exception thrown when a storage operation fails
     * @return a {@link ProblemDetail} body with 500 status
     */
    @ExceptionHandler(StorageException.class)
    public ProblemDetail handleStorageException(StorageException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        detail.setTitle("Storage Error");
        return detail;
    }
}
