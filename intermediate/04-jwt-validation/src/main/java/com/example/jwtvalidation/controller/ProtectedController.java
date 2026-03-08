package com.example.jwtvalidation.controller;

import com.example.jwtvalidation.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Map;

/**
 * Controller that exposes endpoints requiring a valid JWT.
 *
 * <h2>Purpose</h2>
 * <p>These endpoints exist purely to demonstrate that the
 * {@code JwtAuthenticationFilter} is working correctly. A request without a
 * valid JWT will be rejected with {@code 401 Unauthorized} before it ever
 * reaches these methods. A request with a valid JWT from a user who lacks
 * the required role will receive {@code 403 Forbidden}.</p>
 *
 * <h2>How authentication info is available here</h2>
 * <p>By the time any of these methods execute, the filter chain has already:</p>
 * <ol>
 *   <li>Extracted the JWT from the {@code Authorization} header.</li>
 *   <li>Verified the signature and expiry.</li>
 *   <li>Loaded the user from the database.</li>
 *   <li>Stored a {@code UsernamePasswordAuthenticationToken} in the
 *       {@code SecurityContextHolder}.</li>
 * </ol>
 * <p>Controller methods can retrieve this authentication object from the
 * {@link SecurityContextHolder} or have Spring inject it as a parameter.</p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/protected/hello} – accessible to any authenticated user.</li>
 *   <li>{@code GET /api/protected/profile} – returns token claims for the caller.</li>
 *   <li>{@code GET /api/protected/admin} – accessible only to {@code ROLE_ADMIN}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/protected")
public class ProtectedController {

    /**
     * JWT service used here to inspect the token's claims (e.g. role, expiry)
     * and return them in the response for demonstration purposes.
     */
    private final JwtService jwtService;

    public ProtectedController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * Simple greeting endpoint – accessible by any authenticated user.
     *
     * <p>Demonstrates the fundamental outcome of successful JWT validation:
     * the controller knows <em>who</em> is making the request because the
     * filter has already populated the {@code SecurityContextHolder}.</p>
     *
     * <p><b>Example response:</b></p>
     * <pre>{@code
     * { "message": "Hello, alice! Your JWT was successfully validated." }
     * }</pre>
     *
     * @return 200 with a personalised greeting containing the authenticated username
     */
    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello() {
        // Retrieve the authentication object set by JwtAuthenticationFilter.
        // The principal is the UserDetails object loaded from the database.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        return ResponseEntity.ok(Map.of(
                "message", "Hello, " + username + "! Your JWT was successfully validated."
        ));
    }

    /**
     * Profile endpoint – returns detailed information extracted from the JWT.
     *
     * <p>Demonstrates that the token carries all the information needed to
     * identify the user without a database round-trip. The {@link JwtService}
     * parses the raw token from the {@code Authorization} header to extract
     * individual claims.</p>
     *
     * <p><b>Example response:</b></p>
     * <pre>{@code
     * {
     *   "username":  "alice",
     *   "role":      "ROLE_USER",
     *   "expiresAt": "2025-01-01T12:00:00.000+00:00"
     * }
     * }</pre>
     *
     * @param authorizationHeader the raw {@code Authorization} header value
     * @return 200 with the claims extracted from the token
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> profile(
            @RequestHeader("Authorization") String authorizationHeader) {

        // Extract the raw JWT string from "Bearer <token>"
        String token = authorizationHeader.substring(7);

        // Read the username from the SecurityContext (set by JwtAuthenticationFilter)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // Extract individual claims directly from the token to showcase the API
        String role = jwtService.extractRole(token);
        Date expiresAt = jwtService.extractExpiration(token);

        return ResponseEntity.ok(Map.of(
                "username",  username,
                "role",      role != null ? role : "N/A",
                "expiresAt", expiresAt.toString()
        ));
    }

    /**
     * Admin-only endpoint – accessible only to users with {@code ROLE_ADMIN}.
     *
     * <p>The URL-level security rule in {@code SecurityConfig} restricts this
     * endpoint to users with the {@code ROLE_ADMIN} authority. Requests from
     * users with {@code ROLE_USER} will receive {@code 403 Forbidden} even if
     * their JWT is otherwise valid.</p>
     *
     * <p>This demonstrates that a JWT can carry <em>role</em> information that
     * is used for fine-grained access control without requiring a database
     * lookup on every request.</p>
     *
     * <p><b>Example response:</b></p>
     * <pre>{@code
     * { "message": "Welcome to the admin panel, adminuser!" }
     * }</pre>
     *
     * @return 200 for admins, 403 for regular users (enforced by Spring Security)
     */
    @GetMapping("/admin")
    public ResponseEntity<Map<String, String>> adminOnly() {
        // By the time we reach here, Spring Security has already verified that
        // the caller holds ROLE_ADMIN (configured in SecurityConfig).
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        return ResponseEntity.ok(Map.of(
                "message", "Welcome to the admin panel, " + username + "!"
        ));
    }

    /**
     * Introspect endpoint – returns raw details about the current authentication
     * object stored in the {@link SecurityContextHolder}.
     *
     * <p>Useful for debugging: shows exactly what Spring Security knows about
     * the caller after the JWT filter has run.</p>
     *
     * <p><b>Example response:</b></p>
     * <pre>{@code
     * {
     *   "principal":     "alice",
     *   "authorities":   "[ROLE_USER]",
     *   "authenticated": true
     * }
     * }</pre>
     *
     * @return 200 with a snapshot of the current {@code Authentication} object
     */
    @GetMapping("/introspect")
    public ResponseEntity<Map<String, Object>> introspect() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Cast to UserDetails to get the username cleanly
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        return ResponseEntity.ok(Map.of(
                "principal",     userDetails.getUsername(),
                "authorities",   authentication.getAuthorities().toString(),
                "authenticated", authentication.isAuthenticated()
        ));
    }
}
