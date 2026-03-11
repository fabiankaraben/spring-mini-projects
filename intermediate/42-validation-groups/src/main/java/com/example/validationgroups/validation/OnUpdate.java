package com.example.validationgroups.validation;

/**
 * Validation group marker interface for <strong>UPDATE</strong> operations.
 *
 * <p>Constraints tagged with {@code groups = OnUpdate.class} are only activated
 * when the request body is validated with
 * {@code @Validated(OnUpdate.class)} in the controller.  This enables a different
 * set of rules for partial updates (PATCH), where, for instance, the password is
 * not required and the role cannot be changed.</p>
 *
 * <h2>Example – field only editable on update</h2>
 * <pre>{@code
 * @Size(min = 2, max = 100, groups = {OnCreate.class, OnUpdate.class},
 *       message = "Name must be between 2 and 100 characters")
 * @NotBlank(groups = {OnCreate.class, OnUpdate.class},
 *           message = "Name must not be blank")
 * private String name;
 * }</pre>
 *
 * <h2>Example – field NOT validated on update (password)</h2>
 * <pre>{@code
 * // Only @NotBlank on OnCreate; no OnUpdate annotation here.
 * @NotBlank(groups = OnCreate.class, message = "Password is required")
 * private String password;
 * }</pre>
 */
public interface OnUpdate {
    // Marker interface – no methods needed.
}
