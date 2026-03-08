package com.example.exceptiontranslators.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) representing a standard error response structure.
 * Returned to the client when an exception occurs.
 */
public class ErrorResponse {
    /**
     * HTTP status code.
     */
    private int status;

    /**
     * Error message describing the issue.
     */
    private String message;

    /**
     * Timestamp when the error occurred.
     */
    private LocalDateTime timestamp;

    /**
     * Default constructor.
     */
    public ErrorResponse() {
    }

    /**
     * Parameterized constructor.
     *
     * @param status    HTTP status code.
     * @param message   Error message.
     * @param timestamp Timestamp of error.
     */
    public ErrorResponse(int status, String message, LocalDateTime timestamp) {
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
