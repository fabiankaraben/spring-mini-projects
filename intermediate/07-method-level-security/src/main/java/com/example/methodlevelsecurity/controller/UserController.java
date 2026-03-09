package com.example.methodlevelsecurity.controller;

import com.example.methodlevelsecurity.domain.User;
import com.example.methodlevelsecurity.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing user management endpoints.
 *
 * <h2>Security note</h2>
 * <p>Security annotations live on the {@link UserService} methods, not here.
 * This controller is a thin HTTP adapter; the service enforces all access rules
 * regardless of how it is called (HTTP, batch, internal service call).</p>
 *
 * <h2>Annotations used in UserService</h2>
 * <ul>
 *   <li>{@code @Secured("ROLE_ADMIN")}           – {@code getAllUsers}, {@code deleteUser}</li>
 *   <li>{@code @PreAuthorize("hasAnyRole(...)")} – {@code getUserById}</li>
 *   <li>{@code @PreAuthorize("hasRole('ADMIN')")} – {@code updateUserRole}</li>
 * </ul>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET    /api/users/me}         – current user's profile (any authenticated)</li>
 *   <li>{@code GET    /api/users}            – list all users (admin only)</li>
 *   <li>{@code GET    /api/users/{id}}       – find user by ID (admin or moderator)</li>
 *   <li>{@code PATCH  /api/users/{id}/role}  – update user role (admin only)</li>
 *   <li>{@code DELETE /api/users/{id}}       – delete user (admin only)</li>
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
     * Returns the currently authenticated user's own profile.
     *
     * <p>No security annotation needed beyond authentication (which is required by
     * the URL-level rule in SecurityConfig for all {@code /api/**} paths).</p>
     *
     * @return 200 OK with the caller's username and role
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProfile() {
        // The Authentication stored by JwtAuthenticationFilter contains the user's name and role.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("ROLE_USER");

        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "role", role
        ));
    }

    /**
     * Returns a list of all registered users.
     *
     * <p>Protected by {@code @Secured("ROLE_ADMIN")} in {@link UserService#getAllUsers()}.
     * Callers without ROLE_ADMIN will receive 403 Forbidden.</p>
     *
     * @return 200 OK with user list, or 403 Forbidden
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        List<Map<String, Object>> response = users.stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "role", u.getRole().name()
                ))
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Finds a single user by primary key.
     *
     * <p>Protected by {@code @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")}
     * in {@link UserService#getUserById(Long)}.</p>
     *
     * @param id the user's primary key
     * @return 200 OK with user data, 403 Forbidden, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        try {
            User user = userService.getUserById(id);
            return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "role", user.getRole().name()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Updates the role of an existing user.
     *
     * <p>Protected by {@code @PreAuthorize("hasRole('ADMIN')")}
     * in {@link UserService#updateUserRole(Long, String)}.</p>
     *
     * <p><b>Request body example:</b></p>
     * <pre>{@code
     * { "role": "ROLE_MODERATOR" }
     * }</pre>
     *
     * @param id   the user's primary key
     * @param body map containing the new role string
     * @return 200 OK on success, 400 Bad Request for invalid role, 403, or 404
     */
    @PatchMapping("/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        String newRole = body.get("role");
        if (newRole == null || newRole.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Field 'role' is required"));
        }
        try {
            User updated = userService.updateUserRole(id, newRole);
            return ResponseEntity.ok(Map.of(
                    "message", "Role updated",
                    "username", updated.getUsername(),
                    "newRole", updated.getRole().name()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Deletes a user account.
     *
     * <p>Protected by {@code @Secured("ROLE_ADMIN")} in {@link UserService#deleteUser(Long)}.</p>
     *
     * @param id the user's primary key
     * @return 200 OK on success, 403 Forbidden, or 404 Not Found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "User " + id + " deleted successfully"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
