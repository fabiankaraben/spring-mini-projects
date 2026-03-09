package com.example.rolebasedaccess.controller;

import com.example.rolebasedaccess.domain.User;
import com.example.rolebasedaccess.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing user-management endpoints.
 *
 * <h2>Educational focus: @PreAuthorize on controller methods</h2>
 * <p>This controller demonstrates applying {@code @PreAuthorize} directly on
 * HTTP endpoint methods. While the access rules are also enforced at the
 * service layer (in {@link UserService}), annotating the controller adds an
 * explicit first point of rejection with a cleaner HTTP response.</p>
 *
 * <h2>SpEL expressions showcased here</h2>
 * <ul>
 *   <li>{@code hasRole('ADMIN')} – only users with {@code ROLE_ADMIN}.</li>
 *   <li>{@code hasAnyRole('ADMIN', 'MODERATOR')} – admin or moderator.</li>
 *   <li>{@code isAuthenticated()} – any authenticated user, regardless of role.</li>
 * </ul>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/users}            – list all users [ADMIN only]</li>
 *   <li>{@code GET  /api/users/{id}}        – find user by ID [ADMIN, MODERATOR]</li>
 *   <li>{@code GET  /api/users/me}          – current user's profile [any authenticated]</li>
 *   <li>{@code PATCH /api/users/{id}/role}  – update user role [ADMIN only]</li>
 *   <li>{@code DELETE /api/users/{id}}      – delete user [ADMIN only]</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * Returns the list of all registered users.
     *
     * <p><strong>Access:</strong> {@code ROLE_ADMIN} only. The
     * {@code @PreAuthorize} here mirrors the rule in {@link UserService#getAllUsers()}.
     * Both layers must agree; having the annotation in both places means that
     * even a direct service call (not via HTTP) is protected.</p>
     *
     * <p><b>Example response (200 OK):</b></p>
     * <pre>{@code
     * [
     *   { "id": 1, "username": "admin",    "role": "ROLE_ADMIN" },
     *   { "id": 2, "username": "alice",    "role": "ROLE_USER" },
     *   { "id": 3, "username": "moderator","role": "ROLE_MODERATOR" }
     * ]
     * }</pre>
     *
     * @return 200 with the list of users, or 403 if the caller lacks ROLE_ADMIN
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        // Map User entities to response DTOs (never expose the password hash)
        List<Map<String, Object>> response = users.stream()
                .map(u -> Map.<String, Object>of(
                        "id",       u.getId(),
                        "username", u.getUsername(),
                        "role",     u.getRole().name()
                ))
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the currently authenticated user's own profile.
     *
     * <p><strong>Access:</strong> any authenticated user. This is the only
     * "user" endpoint that regular users can access – they can see their own
     * data but cannot list or modify other users.</p>
     *
     * <p><b>Example response (200 OK):</b></p>
     * <pre>{@code
     * { "username": "alice", "role": "ROLE_USER" }
     * }</pre>
     *
     * @return 200 with the caller's username and role
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMyProfile() {
        // The Authentication object in the SecurityContext was set by
        // JwtAuthenticationFilter after it validated the JWT.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("ROLE_USER");

        return ResponseEntity.ok(Map.of(
                "username", username,
                "role",     role
        ));
    }

    /**
     * Finds a single user by their primary key.
     *
     * <p><strong>Access:</strong> {@code ROLE_ADMIN} or {@code ROLE_MODERATOR}.
     * Regular users cannot look up other users' accounts by ID.</p>
     *
     * <p><b>Example response (200 OK):</b></p>
     * <pre>{@code
     * { "id": 2, "username": "alice", "role": "ROLE_USER" }
     * }</pre>
     *
     * @param id the user's primary key from the URL path
     * @return 200 with user data, 403 if access is denied, 404 if not found
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        try {
            User user = userService.getUserById(id);
            return ResponseEntity.ok(Map.of(
                    "id",       user.getId(),
                    "username", user.getUsername(),
                    "role",     user.getRole().name()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Updates the role of an existing user.
     *
     * <p><strong>Access:</strong> {@code ROLE_ADMIN} only. Changing roles is
     * a highly privileged operation – it could escalate a regular user to admin.</p>
     *
     * <p><b>Request body example:</b></p>
     * <pre>{@code
     * { "role": "ROLE_MODERATOR" }
     * }</pre>
     *
     * <p><b>Response (200 OK):</b></p>
     * <pre>{@code
     * { "message": "Role updated", "username": "alice", "newRole": "ROLE_MODERATOR" }
     * }</pre>
     *
     * @param id   the user's primary key from the URL path
     * @param body request body containing the new role string
     * @return 200 on success, 400 for invalid role, 403 if access denied, 404 if not found
     */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        String newRole = body.get("role");
        if (newRole == null || newRole.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Field 'role' is required"));
        }
        try {
            User updated = userService.updateUserRole(id, newRole);
            return ResponseEntity.ok(Map.of(
                    "message",  "Role updated",
                    "username", updated.getUsername(),
                    "newRole",  updated.getRole().name()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Deletes a user account.
     *
     * <p><strong>Access:</strong> {@code ROLE_ADMIN} only.</p>
     *
     * <p><b>Response (200 OK):</b></p>
     * <pre>{@code
     * { "message": "User with id 2 deleted successfully" }
     * }</pre>
     *
     * @param id the user's primary key from the URL path
     * @return 200 on success, 403 if access denied, 404 if not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "User with id " + id + " deleted successfully"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
