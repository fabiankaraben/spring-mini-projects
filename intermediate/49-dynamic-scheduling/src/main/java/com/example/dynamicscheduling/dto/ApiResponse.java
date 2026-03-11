package com.example.dynamicscheduling.dto;

/**
 * Generic API response wrapper used for simple success/failure messages.
 *
 * <p>Used by endpoints that return an action result rather than a resource
 * (e.g., enable/disable/delete endpoints).
 *
 * @param success {@code true} if the operation succeeded
 * @param message human-readable result message
 */
public record ApiResponse(
    boolean success,
    String  message
) {

    /**
     * Creates a successful response with the given message.
     *
     * @param message human-readable success message
     * @return response with {@code success=true}
     */
    public static ApiResponse ok(String message) {
        return new ApiResponse(true, message);
    }

    /**
     * Creates a failure response with the given message.
     *
     * @param message human-readable error message
     * @return response with {@code success=false}
     */
    public static ApiResponse error(String message) {
        return new ApiResponse(false, message);
    }
}
