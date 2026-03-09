package com.example.methodlevelsecurity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for the login request body.
 *
 * <p>Carries the username and plain-text password submitted to
 * {@code POST /api/auth/login}. The service validates the credentials
 * against the stored BCrypt hash.</p>
 */
public class LoginRequest {

    /** The username of the user attempting to log in. */
    @NotBlank(message = "Username must not be blank")
    private String username;

    /** The plain-text password to verify against the stored BCrypt hash. */
    @NotBlank(message = "Password must not be blank")
    private String password;

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }
}
