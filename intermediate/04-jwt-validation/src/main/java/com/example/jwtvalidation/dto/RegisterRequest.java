package com.example.jwtvalidation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) carrying user registration input.
 *
 * <p>DTOs exist to decouple the HTTP layer from the domain model. Rather than
 * binding user input directly to a JPA {@code User} entity (which could expose
 * fields like {@code id} or {@code role} to manipulation), we validate and
 * transfer only what the client is allowed to supply.</p>
 *
 * <p>Bean Validation annotations ({@code @NotBlank}, {@code @Size}) are
 * evaluated by Spring MVC when {@code @Valid} is present on the controller
 * parameter. A failed constraint results in a {@code 400 Bad Request}.</p>
 */
public class RegisterRequest {

    /**
     * The desired login name. Must be 3–50 non-blank characters.
     * Validated before the service layer is invoked.
     */
    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    /**
     * The desired password in plain text. Must be at least 6 characters.
     * The service layer will encode it with BCrypt before persistence.
     * Never logged or persisted in plain text.
     */
    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    /** Required by Jackson for JSON deserialisation. */
    public RegisterRequest() {}

    public RegisterRequest(String username, String password) {
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
