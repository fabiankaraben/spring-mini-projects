package com.example.rolebasedaccess.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO carrying the credentials submitted to the {@code POST /api/auth/login} endpoint.
 *
 * <p>Both fields are required and non-blank; if either is missing the controller
 * returns {@code 400 Bad Request} automatically thanks to the {@code @Valid}
 * annotation on the controller parameter.</p>
 */
public class LoginRequest {

    /** The username submitted by the client. */
    @NotBlank(message = "Username must not be blank")
    private String username;

    /** The plain-text password submitted by the client. */
    @NotBlank(message = "Password must not be blank")
    private String password;

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
