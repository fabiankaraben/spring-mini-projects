package com.example.validationgroups.validation;

/**
 * Validation group marker interface for <strong>PASSWORD CHANGE</strong> operations.
 *
 * <p>This group is applied when the user submits a dedicated "change password" request.
 * Only the constraints tagged with {@code groups = OnPasswordChange.class} are activated,
 * meaning only the password-related fields are validated.  All other fields in the DTO
 * are ignored by the validator for this operation.</p>
 *
 * <h2>Example – constraints applied only when changing the password</h2>
 * <pre>{@code
 * @NotBlank(groups = OnPasswordChange.class, message = "New password is required")
 * @Size(min = 8, groups = OnPasswordChange.class,
 *       message = "Password must be at least 8 characters")
 * private String newPassword;
 *
 * @NotBlank(groups = OnPasswordChange.class,
 *           message = "Password confirmation is required")
 * private String confirmPassword;
 * }</pre>
 *
 * <h2>Controller usage</h2>
 * <pre>{@code
 * @PutMapping("/{id}/change-password")
 * public ResponseEntity<Void> changePassword(
 *         @PathVariable Long id,
 *         @RequestBody @Validated(OnPasswordChange.class) UserRequest request) { ... }
 * }</pre>
 */
public interface OnPasswordChange {
    // Marker interface – no methods needed.
}
