package com.example.validationgroups.validation;

/**
 * Validation group marker interface for <strong>CREATE</strong> operations.
 *
 * <p>Constraints tagged with {@code groups = OnCreate.class} are only activated
 * when the request body is validated with
 * {@code @Validated(OnCreate.class)} in the controller.  This allows fields that
 * are mandatory on creation (e.g. initial password, role) to be enforced only
 * during a POST request and silently skipped during a PATCH request.</p>
 *
 * <h2>Usage in a DTO</h2>
 * <pre>{@code
 * @NotBlank(groups = OnCreate.class, message = "Password is required when creating a user")
 * private String password;
 * }</pre>
 *
 * <h2>Usage in a controller</h2>
 * <pre>{@code
 * @PostMapping
 * public ResponseEntity<UserResponse> create(
 *         @RequestBody @Validated(OnCreate.class) UserRequest request) { ... }
 * }</pre>
 */
public interface OnCreate {
    // Marker interface – no methods needed. The interface name itself acts as the
    // group identifier that Bean Validation uses to filter applicable constraints.
}
