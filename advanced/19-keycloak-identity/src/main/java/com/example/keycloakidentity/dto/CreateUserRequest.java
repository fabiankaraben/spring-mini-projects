package com.example.keycloakidentity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO (Data Transfer Object) for creating a new user record.
 *
 * <p>This DTO is used as the request body for {@code POST /api/users}.
 * Bean Validation annotations are applied here so that Spring validates the input
 * automatically when {@code @Valid} is present on the controller method parameter.
 * If validation fails, Spring returns HTTP 400 Bad Request with field-level error details.
 *
 * <p><b>Why use a DTO instead of the domain object directly?</b>
 * <ul>
 *   <li>The domain {@link com.example.keycloakidentity.domain.User} has fields like
 *       {@code id}, {@code createdAt}, and {@code keycloakId} that the client should NOT
 *       be able to set — they are assigned by the server.</li>
 *   <li>Using a separate DTO creates a clear contract: only the fields listed here
 *       can be provided by the client.</li>
 *   <li>The service layer maps the DTO to the domain object, applying business rules
 *       like setting timestamps and defaulting the {@code active} flag.</li>
 * </ul>
 */
public class CreateUserRequest {

    /**
     * The user's display name (e.g., "John Doe").
     * Required, must be between 2 and 100 characters.
     */
    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
    private String displayName;

    /**
     * The user's email address.
     * Required, must be a valid email format.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    /**
     * The application-level role for this user.
     * Must be either "USER" or "ADMIN".
     * This is stored in our application database (not in Keycloak).
     */
    @NotBlank(message = "Role is required")
    @Pattern(regexp = "USER|ADMIN", message = "Role must be either USER or ADMIN")
    private String role;

    /**
     * Optional: the Keycloak user UUID to associate with this user record.
     * When provided, this links the application user to their Keycloak identity.
     * Corresponds to the {@code sub} claim in Keycloak-issued JWTs.
     */
    private String keycloakId;

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }
}
