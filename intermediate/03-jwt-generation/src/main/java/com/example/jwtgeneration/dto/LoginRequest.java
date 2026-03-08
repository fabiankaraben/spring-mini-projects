package com.example.jwtgeneration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) that carries the credentials submitted by a client
 * to the {@code POST /api/auth/login} endpoint.
 *
 * <p>Using a dedicated DTO (rather than accepting raw {@code @RequestParam}
 * values) gives us:
 * <ul>
 *   <li>Bean Validation support via {@code @Valid} in the controller.</li>
 *   <li>A clear API contract documented through field-level annotations.</li>
 *   <li>Decoupling between the HTTP layer and the domain {@code User} entity.</li>
 * </ul>
 */
public class LoginRequest {

    /**
     * The username submitted by the client. Must not be blank and must be
     * between 3 and 50 characters to match the constraint on the {@code User}
     * entity.
     */
    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    /**
     * The plain-text password submitted by the client. Spring Security will
     * compare this against the stored BCrypt hash via
     * {@code PasswordEncoder#matches()}.
     */
    @NotBlank(message = "Password must not be blank")
    private String password;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by Jackson for JSON deserialisation. */
    public LoginRequest() {}

    public LoginRequest(String username, String password) {
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
