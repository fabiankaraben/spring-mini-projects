package com.example.keycloakidentity.controller;

import com.example.keycloakidentity.domain.User;
import com.example.keycloakidentity.dto.CreateUserRequest;
import com.example.keycloakidentity.dto.UpdateUserRequest;
import com.example.keycloakidentity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the Users API.
 *
 * <p>All endpoints in this controller require a valid Keycloak-issued JWT.
 * The required Keycloak realm role is enforced at two layers:
 * <ol>
 *   <li><b>HTTP layer</b> — in {@link com.example.keycloakidentity.config.SecurityConfig},
 *       which checks the JWT's roles before the request reaches this controller.</li>
 *   <li><b>Method layer</b> — via {@code @PreAuthorize} annotations on individual
 *       methods for fine-grained defense-in-depth.</li>
 * </ol>
 *
 * <h2>Keycloak roles model</h2>
 * This API uses two realm roles defined in Keycloak:
 * <ul>
 *   <li>{@code USER}  — can read user data (GET endpoints)</li>
 *   <li>{@code ADMIN} — can read and write user data (all endpoints)</li>
 * </ul>
 *
 * <h2>JWT principal injection</h2>
 * The {@code @AuthenticationPrincipal Jwt jwt} parameter injects the validated Keycloak
 * JWT directly into handler methods. This gives access to all Keycloak claims:
 * <ul>
 *   <li>{@code jwt.getSubject()} — the Keycloak user UUID ({@code sub} claim)</li>
 *   <li>{@code jwt.getClaimAsString("preferred_username")} — the Keycloak username</li>
 *   <li>{@code jwt.getClaimAsString("email")} — the user's email from Keycloak</li>
 *   <li>{@code jwt.getClaimAsMap("realm_access")} — the realm roles map</li>
 * </ul>
 *
 * <h2>Endpoints summary</h2>
 * <ul>
 *   <li>{@code GET    /api/users}         — list all users or filter by role (USER/ADMIN)</li>
 *   <li>{@code GET    /api/users/me}      — current user's own profile (any authenticated)</li>
 *   <li>{@code GET    /api/users/{id}}    — get user by ID (USER/ADMIN)</li>
 *   <li>{@code POST   /api/users}         — create user (ADMIN only)</li>
 *   <li>{@code PUT    /api/users/{id}}    — update user (ADMIN only)</li>
 *   <li>{@code DELETE /api/users/{id}}   — delete user (ADMIN only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    /**
     * The service implementing user business logic.
     * Constructor injection is preferred — makes the dependency explicit and testable.
     */
    private final UserService userService;

    /**
     * Constructs the controller with its required service dependency.
     *
     * @param userService the service that handles user CRUD operations
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Lists all application users, or filters by role if the {@code role} query
     * parameter is provided.
     *
     * <p>Requires Keycloak realm role {@code USER} or {@code ADMIN}.
     *
     * @param role optional query parameter for role filtering (e.g., "ADMIN" or "USER")
     * @param jwt  the validated Keycloak JWT of the caller (injected by Spring)
     * @return HTTP 200 with the list of users (may be empty)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<User>> listUsers(
            @RequestParam(required = false) String role,
            @AuthenticationPrincipal Jwt jwt) {

        // Log the caller's Keycloak username for audit purposes
        // In production, use a proper structured audit log framework
        String callerUsername = jwt != null ? jwt.getClaimAsString("preferred_username") : "unknown";

        List<User> users;
        if (role != null && !role.isBlank()) {
            // Filter by role if the query parameter was provided
            users = userService.getUsersByRole(role);
        } else {
            // Return all users
            users = userService.getAllUsers();
        }

        // HTTP 200 with the list — even if empty (empty list ≠ error)
        return ResponseEntity.ok(users);
    }

    /**
     * Returns the application profile of the currently authenticated user.
     *
     * <p>This endpoint demonstrates the key Keycloak integration pattern:
     * extract the caller's Keycloak UUID from the JWT's {@code sub} claim, then
     * look up the corresponding application user record by that Keycloak UUID.
     *
     * <p>Any valid authenticated user can access this endpoint (no specific role required).
     *
     * <p>Returns HTTP 404 if the authenticated Keycloak user has no corresponding
     * record in the application database (e.g., they exist in Keycloak but haven't
     * been registered in this application yet).
     *
     * @param jwt the validated Keycloak JWT (injected by Spring Security)
     * @return HTTP 200 with the user's profile, or HTTP 404 if not found in the app DB
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt) {

        // Extract identity claims directly from the Keycloak JWT
        String keycloakId = jwt.getSubject(); // "sub" claim = Keycloak user UUID
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name"); // full name from Keycloak token

        // Build the response combining Keycloak identity + application profile
        Map<String, Object> response = new LinkedHashMap<>();

        // Section 1: Keycloak identity claims (from the JWT itself)
        response.put("keycloakId", keycloakId);
        response.put("preferredUsername", preferredUsername);
        response.put("email", email);
        response.put("name", name);

        // Section 2: Application-level profile (from our database, if it exists)
        userService.getUserByKeycloakId(keycloakId)
                .ifPresentOrElse(
                        appUser -> {
                            // User has an application profile — include it
                            response.put("appUserId", appUser.getId());
                            response.put("appRole", appUser.getRole());
                            response.put("active", appUser.isActive());
                            response.put("createdAt", appUser.getCreatedAt());
                        },
                        () -> {
                            // User is authenticated but has no application profile yet
                            // This is normal for newly registered Keycloak users
                            response.put("appProfile", "Not registered in application database");
                        }
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a single user by their application-internal ID.
     *
     * <p>Requires Keycloak realm role {@code USER} or {@code ADMIN}.
     *
     * @param id  the application-level user ID from the URL path variable
     * @param jwt the validated Keycloak JWT of the caller
     * @return HTTP 200 with the user, or HTTP 404 if not found
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<User> getUser(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        return userService.getUserById(id)
                .map(ResponseEntity::ok)           // 200 OK if found
                .orElse(ResponseEntity.notFound().build()); // 404 Not Found if not found
    }

    /**
     * Creates a new application user record.
     *
     * <p>Requires Keycloak realm role {@code ADMIN}.
     *
     * <p>The request body is validated by Bean Validation via {@code @Valid}.
     * If validation fails, Spring returns HTTP 400 with field-level error details.
     *
     * <p>Returns HTTP 201 Created with the newly created user in the response body.
     *
     * @param request the create request body (validated)
     * @param jwt     the validated Keycloak JWT of the admin caller
     * @return HTTP 201 Created with the created user
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        User created = userService.createUser(request);

        // HTTP 201 Created is the correct REST status for successful resource creation
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates an existing application user record.
     *
     * <p>Requires Keycloak realm role {@code ADMIN}.
     *
     * <p>Only the non-null fields in the request body are applied (partial update).
     * Fields omitted from the JSON body are left unchanged.
     *
     * @param id      the ID of the user to update
     * @param request the update request body (validated; only non-null fields applied)
     * @param jwt     the validated Keycloak JWT of the admin caller
     * @return HTTP 200 with the updated user, or HTTP 404 if not found
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return userService.updateUser(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes an application user record by ID.
     *
     * <p>Requires Keycloak realm role {@code ADMIN}.
     *
     * <p>Returns HTTP 204 No Content on successful deletion (no body in response per REST
     * convention). Returns HTTP 404 if no user with the given ID exists.
     *
     * @param id  the ID of the user to delete
     * @param jwt the validated Keycloak JWT of the admin caller
     * @return HTTP 204 No Content if deleted, HTTP 404 if not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        boolean deleted = userService.deleteUser(id);

        if (deleted) {
            // HTTP 204 No Content: success with no response body
            return ResponseEntity.noContent().build();
        } else {
            // HTTP 404 Not Found: no user had this ID
            return ResponseEntity.notFound().build();
        }
    }
}
