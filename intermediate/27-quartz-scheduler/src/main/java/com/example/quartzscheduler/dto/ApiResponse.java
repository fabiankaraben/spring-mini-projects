package com.example.quartzscheduler.dto;

/**
 * Generic API response wrapper used for simple success/error messages.
 *
 * <p>Returned by endpoints that do not need to return complex data, such as
 * pause, resume, trigger-now, and delete operations.
 *
 * @param success {@code true} if the operation completed without errors
 * @param message human-readable description of the outcome
 */
public record ApiResponse(boolean success, String message) {

    /**
     * Convenience factory method for a successful response.
     *
     * @param message description of what was done
     * @return ApiResponse with {@code success=true}
     */
    public static ApiResponse ok(String message) {
        return new ApiResponse(true, message);
    }

    /**
     * Convenience factory method for an error response.
     *
     * @param message description of the error
     * @return ApiResponse with {@code success=false}
     */
    public static ApiResponse error(String message) {
        return new ApiResponse(false, message);
    }
}
