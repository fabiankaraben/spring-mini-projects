package com.example.keycloakidentity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for partially updating an existing user record.
 *
 * <p>This DTO is used as the request body for {@code PUT /api/users/{id}}.
 * All fields are <em>optional</em>: only non-null fields in this DTO are applied
 * to the existing user record. This implements partial-update semantics, allowing
 * a client to update just the email without resending all other fields.
 *
 * <p><b>Why all fields are nullable here:</b>
 * Unlike {@link CreateUserRequest} where fields are required, an update request
 * should allow the client to send only the fields they want to change. A null field
 * in this DTO means "do not change this field" — the service layer implements this logic.
 *
 * <p><b>Note on the {@code active} field:</b>
 * A {@code Boolean} (boxed) is used instead of {@code boolean} (primitive) because
 * primitives cannot be null in Java. When the client does not include this field in
 * the JSON body, Jackson sets it to null, and the service layer skips it.
 */
public class UpdateUserRequest {

    /**
     * New display name for the user. Optional.
     * If null, the existing display name is kept unchanged.
     */
    @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
    private String displayName;

    /**
     * New email address for the user. Optional.
     * If null, the existing email is kept unchanged.
     */
    @Email(message = "Email must be a valid email address")
    private String email;

    /**
     * New application-level role. Optional.
     * Must be "USER" or "ADMIN" if provided.
     */
    @Pattern(regexp = "USER|ADMIN", message = "Role must be either USER or ADMIN")
    private String role;

    /**
     * New active status. Optional.
     * Uses boxed Boolean (not primitive) so that null means "do not change".
     */
    private Boolean active;

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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
