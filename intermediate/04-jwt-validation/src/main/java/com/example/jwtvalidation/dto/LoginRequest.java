package com.example.jwtvalidation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object (DTO) for login credential input.
 *
 * <p>Carries the username and password submitted by the client on the
 * {@code POST /api/auth/login} endpoint. The credentials are validated
 * before being passed to the {@code AuthenticationManager}.</p>
 */
public class LoginRequest {

    /**
     * The login name of the user attempting to authenticate.
     */
    @NotBlank(message = "Username must not be blank")
    private String username;

    /**
     * The plain-text password submitted by the client.
     * This is compared against the stored BCrypt hash by
     * {@code DaoAuthenticationProvider} – it is never stored or logged.
     */
    @NotBlank(message = "Password must not be blank")
    private String password;

    /** Required by Jackson for JSON deserialisation. */
    public LoginRequest() {}

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

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
