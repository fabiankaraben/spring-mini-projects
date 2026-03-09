package com.example.methodlevelsecurity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for the user registration request body.
 *
 * <p>Carries the username, plain-text password, and optional role string sent by
 * the client in {@code POST /api/auth/register}. Bean Validation annotations
 * enforce basic constraints before the request reaches the service layer.</p>
 */
public class RegisterRequest {

    /**
     * Desired username. Must be 3–50 non-blank characters.
     */
    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    /**
     * Plain-text password provided by the user.
     * The service layer encodes it with BCrypt before persisting.
     */
    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    /**
     * Optional role string (e.g. {@code "ROLE_ADMIN"}).
     * If absent or unrecognised, the service defaults to {@code ROLE_USER}.
     */
    private String role;

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }

    public void setRole(String role) { this.role = role; }
}
