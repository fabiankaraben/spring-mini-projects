package com.example.rolebasedaccess.controller;

import com.example.rolebasedaccess.dto.LoginRequest;
import com.example.rolebasedaccess.dto.LoginResponse;
import com.example.rolebasedaccess.dto.RegisterRequest;
import com.example.rolebasedaccess.service.JwtService;
import com.example.rolebasedaccess.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller handling authentication-related endpoints (public access).
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /api/auth/register} – create a new user account.</li>
 *   <li>{@code POST /api/auth/login}    – authenticate and receive a JWT.</li>
 * </ul>
 *
 * <h2>Login flow</h2>
 * <ol>
 *   <li>Client POSTs {@code { "username": "...", "password": "..." }}.</li>
 *   <li>The controller passes credentials to the {@link AuthenticationManager},
 *       which delegates to {@code DaoAuthenticationProvider}.</li>
 *   <li>The provider loads the user from PostgreSQL and verifies the BCrypt hash.</li>
 *   <li>On success, the controller generates a signed JWT via {@link JwtService}.</li>
 *   <li>The client stores the token and includes it in the
 *       {@code Authorization: Bearer <token>} header of subsequent requests.</li>
 * </ol>
 *
 * <p>These endpoints are publicly accessible (no JWT required) because they are
 * matched by {@code .requestMatchers("/api/auth/**").permitAll()} in
 * {@code SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Spring Security's central authentication gateway. */
    private final AuthenticationManager authenticationManager;

    /** Service responsible for building and validating JWTs. */
    private final JwtService jwtService;

    /** Service responsible for user registration. */
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * Registers a new user account.
     *
     * <p>The password is BCrypt-encoded inside {@link UserService} before
     * persistence. An optional {@code role} field in the request body allows
     * specifying a role (e.g. {@code "ROLE_ADMIN"}); it defaults to
     * {@code ROLE_USER} if absent or invalid.</p>
     *
     * <p><b>Request body example:</b></p>
     * <pre>{@code
     * { "username": "alice", "password": "secret123" }
     * }</pre>
     *
     * <p>Register as admin (for demo purposes):</p>
     * <pre>{@code
     * { "username": "admin", "password": "admin123", "role": "ROLE_ADMIN" }
     * }</pre>
     *
     * <p><b>Response (201 Created):</b></p>
     * <pre>{@code
     * { "message": "User 'alice' registered successfully with role ROLE_USER" }
     * }</pre>
     *
     * @param request validated DTO with username, password, and optional role
     * @return 201 with a success message, or 409 if the username is taken
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            var user = userService.registerUser(request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(Map.of("message",
                            "User '" + user.getUsername() + "' registered successfully with role " + user.getRole()));
        } catch (IllegalArgumentException ex) {
            // Username already exists – return 409 Conflict
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Authenticates a user and returns a signed JWT on success.
     *
     * <p><b>Request body example:</b></p>
     * <pre>{@code
     * { "username": "alice", "password": "secret123" }
     * }</pre>
     *
     * <p><b>Response (200 OK):</b></p>
     * <pre>{@code
     * {
     *   "token": "eyJhbGciOiJIUzI1NiJ9...",
     *   "tokenType": "Bearer",
     *   "username": "alice",
     *   "role": "ROLE_USER",
     *   "expiresInSeconds": 3600
     * }
     * }</pre>
     *
     * @param request validated DTO with username and password
     * @return 200 with a {@link LoginResponse} containing the JWT, or 401 on failure
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // Delegate authentication to Spring Security.
            // DaoAuthenticationProvider loads the user from DB and verifies the BCrypt hash.
            // Throws BadCredentialsException if credentials are wrong.
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Extract the authenticated principal (UserDetails).
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // Generate a signed JWT embedding the user's role in the payload.
            String token = jwtService.generateToken(userDetails);

            // Extract the role string from the authorities list for the response body.
            String role = userDetails.getAuthorities().stream()
                    .findFirst()
                    .map(a -> a.getAuthority())
                    .orElse("ROLE_USER");

            LoginResponse loginResponse = new LoginResponse(
                    token,
                    userDetails.getUsername(),
                    role,
                    jwtService.getExpirationSeconds()
            );

            return ResponseEntity.ok(loginResponse);

        } catch (BadCredentialsException ex) {
            // Spring Security maps both "username not found" and "wrong password"
            // to the same exception to avoid leaking whether a username exists.
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }
}
