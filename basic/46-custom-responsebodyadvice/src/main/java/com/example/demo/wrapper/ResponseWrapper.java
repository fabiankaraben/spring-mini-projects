package com.example.demo.wrapper;

import java.time.LocalDateTime;

/**
 * A generic wrapper class for API responses.
 * <p>
 * This class ensures that all API responses follow a consistent structure,
 * containing a timestamp, status code, message, and the actual data payload.
 * </p>
 *
 * @param <T> The type of the data payload.
 */
public class ResponseWrapper<T> {
    private LocalDateTime timestamp;
    private int status;
    private String message;
    private T data;

    public ResponseWrapper() {
    }

    public ResponseWrapper(LocalDateTime timestamp, int status, String message, T data) {
        this.timestamp = timestamp;
        this.status = status;
        this.message = message;
        this.data = data;
    }

    /**
     * Creates a success response wrapper with the given data.
     *
     * @param data The payload to be wrapped.
     * @param <T>  The type of the payload.
     * @return A ResponseWrapper instance with status 200 and the provided data.
     */
    public static <T> ResponseWrapper<T> success(T data) {
        return new ResponseWrapper<>(LocalDateTime.now(), 200, "Success", data);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
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

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
