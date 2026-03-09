package com.example.rolebasedaccess.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller exposing moderator-level endpoints.
 *
 * <h2>Educational focus</h2>
 * <p>This controller demonstrates the {@code hasAnyRole} SpEL expression, which
 * allows a method to be accessed by multiple roles. Both
 * {@code ROLE_MODERATOR} and {@code ROLE_ADMIN} users may call these endpoints.</p>
 *
 * <h2>Why ADMIN can access MODERATOR endpoints</h2>
 * <p>Administrators outrank moderators in the role hierarchy. Using
 * {@code hasAnyRole('ADMIN', 'MODERATOR')} explicitly grants admins access too.
 * Spring Security also supports configuring a formal {@code RoleHierarchy} bean
 * (where ADMIN ⊇ MODERATOR ⊇ USER), but the explicit listing used here is
 * simpler and more educational for a mini-project.</p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/moderator/panel} – moderator control panel [MODERATOR, ADMIN]</li>
 *   <li>{@code GET /api/moderator/reports} – view content reports [MODERATOR, ADMIN]</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/moderator")
public class ModeratorController {

    /**
     * Moderator panel endpoint.
     *
     * <p><strong>Access:</strong> {@code ROLE_MODERATOR} and {@code ROLE_ADMIN}.
     * Regular users with only {@code ROLE_USER} will receive {@code 403 Forbidden}.</p>
     *
     * <p><b>Example response (200 OK):</b></p>
     * <pre>{@code
     * {
     *   "message":     "Welcome to the moderator panel, moderatoruser!",
     *   "description": "You have content moderation permissions."
     * }
     * }</pre>
     *
     * @return 200 for moderators and admins, 403 for regular users
     */
    @GetMapping("/panel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Map<String, String>> moderatorPanel() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("ROLE_USER");

        return ResponseEntity.ok(Map.of(
                "message",     "Welcome to the moderator panel, " + username + "!",
                "description", "You have content moderation permissions.",
                "yourRole",    role
        ));
    }

    /**
     * Content reports endpoint – simulates listing flagged content reports.
     *
     * <p><strong>Access:</strong> {@code ROLE_MODERATOR} and {@code ROLE_ADMIN}.</p>
     *
     * <p><b>Example response (200 OK):</b></p>
     * <pre>{@code
     * {
     *   "pendingReports": 3,
     *   "description":    "Review pending content reports in the moderation queue."
     * }
     * }</pre>
     *
     * @return 200 with simulated report data
     */
    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Map<String, Object>> getReports() {
        return ResponseEntity.ok(Map.of(
                "pendingReports", 3,
                "description",   "Review pending content reports in the moderation queue."
        ));
    }
}
