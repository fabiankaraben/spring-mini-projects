package com.example.jwtgeneration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for user registration requests sent to
 * {@code POST /api/auth/register}.
 *
 * <p>Registration allows the integration tests (and manual testing) to create
 * users programmatically without needing a database seed script. In a production
 * system this endpoint would typically be admin-only or handled out-of-band.
 */
public class RegisterRequest {

    /**
     * Desired username for the new account. Must be unique across all existing
     * users (enforced in {@code UserService}).
     */
    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    /**
     * Plain-text password that will be BCrypt-encoded before storage.
     * A minimum length of 6 characters is enforced here.
     */
    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by Jackson for JSON deserialisation. */
    public RegisterRequest() {}

    public RegisterRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

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
