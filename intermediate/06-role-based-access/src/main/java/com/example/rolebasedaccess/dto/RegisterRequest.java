package com.example.rolebasedaccess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO (Data Transfer Object) carrying the data submitted to the registration endpoint.
 *
 * <p>Bean Validation annotations on this class are enforced by the
 * {@code @Valid} annotation on the controller method parameter. If any
 * constraint is violated, Spring automatically returns {@code 400 Bad Request}
 * before the controller body even runs.</p>
 */
public class RegisterRequest {

    /**
     * The desired username. Must be 3–50 non-blank characters.
     * A separate uniqueness check is performed in {@code UserService}.
     */
    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    /**
     * The plain-text password. Must be at least 6 characters.
     * BCrypt hashing is applied in {@code UserService} before storage.
     */
    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    /**
     * Optional role override. When not provided (null), the default
     * {@code ROLE_USER} is assigned in {@code UserService}.
     * In a real production system this field would typically not be
     * exposed to end users – admins would assign roles separately.
     * It is included here for educational / demo purposes.
     */
    private String role;

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
