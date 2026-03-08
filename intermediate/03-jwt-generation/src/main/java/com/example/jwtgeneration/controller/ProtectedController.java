package com.example.jwtgeneration.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A simple protected endpoint used to demonstrate that the JWT returned by
 * the login endpoint is structurally valid and can be manually inspected.
 *
 * <p><strong>Note:</strong> In this project ({@code 03-jwt-generation}) the
 * endpoint simply echoes the raw token back to the caller for inspection.
 * Full server-side JWT <em>validation</em> – where the server verifies the
 * signature and sets the {@code SecurityContext} on every request – is the
 * subject of the next mini-project {@code 04-jwt-validation}.
 *
 * <p>The endpoint is reachable only with HTTP Basic authentication (the
 * default Spring Security fallback when no JWT filter is installed) during
 * automated tests, and is accessible with a Bearer token header for manual
 * curl testing as described in the README.
 */
@RestController
@RequestMapping("/api/protected")
public class ProtectedController {

    /**
     * Returns information about the token presented in the
     * {@code Authorization} header, along with a greeting.
     *
     * <p>This endpoint is intentionally minimal. Its sole purpose is to give
     * developers a concrete URL to call after obtaining a token from
     * {@code /api/auth/login} to confirm that the flow works end-to-end.
     *
     * <p><b>Request header:</b>
     * <pre>{@code
     * Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     * }</pre>
     *
     * <p><b>Response (200 OK):</b>
     * <pre>{@code
     * {
     *   "message": "You have successfully authenticated!",
     *   "hint": "Paste your token at https://jwt.io to inspect its claims."
     * }
     * }</pre>
     *
     * @param authHeader the raw {@code Authorization} header value (optional
     *                   so the endpoint does not fail when called without one
     *                   during Basic-auth integration tests)
     * @return a JSON body with a success message
     */
    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // Simply acknowledge the request. JWT validation (signature check,
        // expiry check, setting SecurityContext) is out of scope for this project.
        return ResponseEntity.ok(Map.of(
                "message", "You have successfully authenticated!",
                "hint", "Paste your token at https://jwt.io to inspect its claims."
        ));
    }
}
