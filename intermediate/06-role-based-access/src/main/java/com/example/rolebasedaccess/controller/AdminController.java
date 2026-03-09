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
 * REST controller exposing admin-only endpoints.
 *
 * <h2>Double-layer protection</h2>
 * <p>These endpoints are protected at <strong>two independent layers</strong>:</p>
 * <ol>
 *   <li><strong>URL-level rule</strong> in {@code SecurityConfig}:
 *       {@code .requestMatchers("/api/admin/**").hasRole("ADMIN")} – Spring Security
 *       rejects the request before it reaches this controller if the JWT does
 *       not carry {@code ROLE_ADMIN}.</li>
 *   <li><strong>Method-level rule</strong> via {@code @PreAuthorize("hasRole('ADMIN')")}
 *       on each method – this provides defence-in-depth: even if the URL rule
 *       were accidentally relaxed, the method annotation still enforces access.</li>
 * </ol>
 *
 * <h2>Why both layers?</h2>
 * <p>URL rules are evaluated by a filter <em>before</em> the controller is
 * involved. Method rules are evaluated by an AOP proxy <em>when the method is
 * called</em>. Having both means:</p>
 * <ul>
 *   <li>The request is rejected early (cheaper) at the filter level.</li>
 *   <li>The service/controller is protected even when called programmatically
 *       from non-HTTP code (e.g. event listeners, scheduled tasks).</li>
 * </ul>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/admin/dashboard} – summary dashboard data [ADMIN only]</li>
 *   <li>{@code GET /api/admin/system}     – system status info [ADMIN only]</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /**
     * Admin dashboard endpoint.
     *
     * <p><strong>Access:</strong> {@code ROLE_ADMIN} only (enforced at both URL
     * and method level).</p>
     *
     * <p><b>Example response (200 OK):</b></p>
     * <pre>{@code
     * {
     *   "message": "Welcome to the admin dashboard, adminuser!",
     *   "description": "This endpoint is protected by both URL-level security and @PreAuthorize."
     * }
     * }</pre>
     *
     * @return 200 for admins, 403 for non-admins (or 401 if not authenticated)
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> dashboard() {
        // Retrieve the authenticated principal from the SecurityContext.
        // JwtAuthenticationFilter set this before the request reached here.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        return ResponseEntity.ok(Map.of(
                "message",     "Welcome to the admin dashboard, " + username + "!",
                "description", "This endpoint is protected by both URL-level security and @PreAuthorize."
        ));
    }

    /**
     * System status endpoint – returns simulated system information.
     *
     * <p><strong>Access:</strong> {@code ROLE_ADMIN} only.</p>
     *
     * <p>In a real application this might return metrics such as active users,
     * memory usage, or background job statuses.</p>
     *
     * <p><b>Example response (200 OK):</b></p>
     * <pre>{@code
     * {
     *   "status":      "UP",
     *   "javaVersion": "21",
     *   "description": "All systems operational"
     * }
     * }</pre>
     *
     * @return 200 with simulated system info
     */
    @GetMapping("/system")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> systemStatus() {
        return ResponseEntity.ok(Map.of(
                "status",      "UP",
                "javaVersion", System.getProperty("java.version"),
                "description", "All systems operational"
        ));
    }
}
