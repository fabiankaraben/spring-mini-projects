package com.example.validationgroups.controller;

import com.example.validationgroups.dto.UserRequest;
import com.example.validationgroups.dto.UserResponse;
import com.example.validationgroups.service.UserService;
import com.example.validationgroups.validation.OnCreate;
import com.example.validationgroups.validation.OnPasswordChange;
import com.example.validationgroups.validation.OnUpdate;
import com.example.validationgroups.validation.UserValidationSequence;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user management.
 *
 * <h2>Validation Groups in Action</h2>
 * <p>Each endpoint uses {@code @Validated} with a specific group or sequence to
 * activate only the relevant constraints for that operation.  The same
 * {@link UserRequest} DTO is reused across all endpoints, but each time a
 * different subset of constraints is enforced.</p>
 *
 * <h2>Endpoint → Validation Group mapping</h2>
 * <pre>
 * POST   /api/users             →  @Validated(UserValidationSequence.class)
 *                                  Activates: Default + OnCreate
 *                                  Required fields: name, email, password, role
 *
 * PATCH  /api/users/{id}        →  @Validated(OnUpdate.class)
 *                                  Activates: OnUpdate only
 *                                  Required fields: name, email
 *                                  Ignored: password, role, newPassword, confirmPassword
 *
 * PUT    /api/users/{id}/password → @Validated(OnPasswordChange.class)
 *                                  Activates: OnPasswordChange only
 *                                  Required fields: newPassword, confirmPassword
 *                                  Ignored: name, email, password, role
 * </pre>
 *
 * <p>If you submit a create request without a password, only the
 * {@code OnCreate}-group {@code @NotBlank} on the {@code password} field fires.
 * If you submit an update request without a password, no error is produced
 * because there is no {@code OnUpdate} constraint on the {@code password} field.</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    /**
     * Constructor injection – preferred over field injection for testability.
     *
     * @param userService the service that contains business logic
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ── Read endpoints ────────────────────────────────────────────────────────

    /**
     * Returns all users.
     *
     * <p>No validation group is involved here – this is a pure read operation.</p>
     *
     * @return HTTP 200 with a list of {@link UserResponse} objects
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> listAll() {
        List<UserResponse> responses = userService.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Returns a single user by ID.
     *
     * @param id the user's primary key
     * @return HTTP 200 with the {@link UserResponse}, or HTTP 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        UserResponse response = UserResponse.from(userService.findById(id));
        return ResponseEntity.ok(response);
    }

    /**
     * Searches for users whose names contain the given keyword (case-insensitive).
     *
     * @param name the keyword to search for
     * @return HTTP 200 with a (possibly empty) list of matching users
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> search(@RequestParam String name) {
        List<UserResponse> responses = userService.searchByName(name)
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Returns all users with the specified role.
     *
     * @param role the role to filter by (e.g. "ADMIN", "USER")
     * @return HTTP 200 with a (possibly empty) list of users
     */
    @GetMapping("/role/{role}")
    public ResponseEntity<List<UserResponse>> byRole(@PathVariable String role) {
        List<UserResponse> responses = userService.findByRole(role)
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // ── Write endpoints ───────────────────────────────────────────────────────

    /**
     * Creates a new user.
     *
     * <h3>Validation Group: {@link UserValidationSequence} (Default → OnCreate)</h3>
     * <p>Using the sequence ensures that standard constraints ({@code @Email},
     * {@code @Size}) are checked first.  Only if those pass does the validator
     * check the {@code OnCreate}-specific constraints (required password, required role).
     * This produces clean, ordered error messages.</p>
     *
     * <p><strong>Required fields:</strong> name, email, password, role.</p>
     * <p><strong>Ignored fields:</strong> newPassword, confirmPassword.</p>
     *
     * @param request the request body validated with {@code UserValidationSequence}
     * @return HTTP 201 Created with the {@link UserResponse}
     */
    @PostMapping
    public ResponseEntity<UserResponse> create(
            @RequestBody @Validated(UserValidationSequence.class) UserRequest request) {
        UserResponse response = UserResponse.from(userService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Partially updates an existing user's editable fields.
     *
     * <h3>Validation Group: {@link OnUpdate}</h3>
     * <p>Only the constraints tagged with {@code OnUpdate} are activated.
     * This means:</p>
     * <ul>
     *   <li>{@code name} – must be non-blank and 2–100 characters (required).</li>
     *   <li>{@code email} – must be non-blank and a valid email address (required).</li>
     *   <li>{@code password} – <strong>NOT validated</strong> and NOT used.
     *       Sending a password in an update request is silently ignored.</li>
     *   <li>{@code role} – <strong>NOT validated</strong> and NOT used.
     *       The role cannot be changed through this endpoint by design.</li>
     * </ul>
     *
     * @param id      the ID of the user to update
     * @param request the request body validated with {@code OnUpdate}
     * @return HTTP 200 OK with the updated {@link UserResponse}
     */
    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @RequestBody @Validated(OnUpdate.class) UserRequest request) {
        UserResponse response = UserResponse.from(userService.update(id, request));
        return ResponseEntity.ok(response);
    }

    /**
     * Changes the password of an existing user.
     *
     * <h3>Validation Group: {@link OnPasswordChange}</h3>
     * <p>Only the constraints tagged with {@code OnPasswordChange} are activated.
     * This means:</p>
     * <ul>
     *   <li>{@code newPassword} – required, minimum 8 characters.</li>
     *   <li>{@code confirmPassword} – required.</li>
     *   <li>All other fields ({@code name}, {@code email}, {@code password}, {@code role})
     *       are completely <strong>ignored</strong> by the validator.</li>
     * </ul>
     *
     * <p>The service additionally validates that {@code newPassword} equals
     * {@code confirmPassword} (cross-field check).</p>
     *
     * @param id      the ID of the user whose password should be changed
     * @param request the request body validated with {@code OnPasswordChange}
     * @return HTTP 204 No Content on success
     */
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @RequestBody @Validated(OnPasswordChange.class) UserRequest request) {
        userService.changePassword(id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a user by ID.
     *
     * @param id the ID of the user to delete
     * @return HTTP 204 No Content, or HTTP 404 if the user does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
