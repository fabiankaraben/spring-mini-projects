package com.example.jwtgeneration.controller;

import com.example.jwtgeneration.dto.LoginRequest;
import com.example.jwtgeneration.dto.LoginResponse;
import com.example.jwtgeneration.dto.RegisterRequest;
import com.example.jwtgeneration.service.JwtService;
import com.example.jwtgeneration.service.UserService;
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
 * REST controller that handles authentication-related HTTP endpoints.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /api/auth/register} – create a new user account.</li>
 *   <li>{@code POST /api/auth/login}    – authenticate and receive a JWT.</li>
 * </ul>
 *
 * <h2>Login flow (the core of this project)</h2>
 * <ol>
 *   <li>Client POSTs {@code { "username": "...", "password": "..." }} as JSON.</li>
 *   <li>The controller passes the credentials to the
 *       {@link AuthenticationManager}, which delegates to
 *       {@code DaoAuthenticationProvider}.</li>
 *   <li>{@code DaoAuthenticationProvider} loads the user from PostgreSQL via
 *       {@code UserDetailsServiceImpl} and verifies the password with BCrypt.</li>
 *   <li>On success, an {@link Authentication} object with the {@link UserDetails}
 *       principal is returned.</li>
 *   <li>The {@link JwtService} builds a signed JWT from the {@link UserDetails}
 *       and the controller returns it in a {@link LoginResponse} JSON body.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Spring Security's central authentication gateway.
     * Injecting it here lets the controller trigger authentication programmatically
     * instead of relying on the filter chain.
     */
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
     * persistence. All new users receive the {@code ROLE_USER} role.
     *
     * <p><b>Request body example:</b>
     * <pre>{@code
     * { "username": "alice", "password": "secret123" }
     * }</pre>
     *
     * <p><b>Response (201 Created):</b>
     * <pre>{@code
     * { "message": "User 'alice' registered successfully" }
     * }</pre>
     *
     * @param request validated DTO with username and password
     * @return 201 with a success message, or 409 if the username is taken
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            userService.registerUser(request);
            // Return 201 Created with a plain confirmation message
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(Map.of("message", "User '" + request.getUsername() + "' registered successfully"));
        } catch (IllegalArgumentException ex) {
            // Username already exists – return 409 Conflict with the error message
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Authenticates a user and returns a signed JWT on success.
     *
     * <p>The {@link AuthenticationManager} handles password verification; this
     * controller only orchestrates the call and converts the result into a JWT.
     *
     * <p><b>Request body example:</b>
     * <pre>{@code
     * { "username": "alice", "password": "secret123" }
     * }</pre>
     *
     * <p><b>Response (200 OK):</b>
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
     * @return 200 with a {@link LoginResponse} containing the JWT, or 401 if
     *         authentication fails ({@code BadCredentialsException} caught and
     *         converted to a 401 Unauthorized response)
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // Step 1: Delegate authentication to Spring Security.
            // UsernamePasswordAuthenticationToken is the standard token type for
            // username/password credentials. The AuthenticationManager will:
            //   a) load the user from DB via UserDetailsServiceImpl
            //   b) compare the submitted password to the stored BCrypt hash
            //   c) throw BadCredentialsException if they don't match
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Step 2: Extract the authenticated principal (UserDetails).
            // After authenticate() succeeds, getPrincipal() returns the UserDetails
            // object that UserDetailsServiceImpl constructed.
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // Step 3: Generate a signed JWT for this user.
            String token = jwtService.generateToken(userDetails);

            // Step 4: Extract the role from the authorities list for the response.
            String role = userDetails.getAuthorities().stream()
                    .findFirst()
                    .map(a -> a.getAuthority())
                    .orElse("ROLE_USER");

            // Step 5: Build and return the response DTO with the token and metadata.
            LoginResponse response = new LoginResponse(
                    token,
                    userDetails.getUsername(),
                    role,
                    jwtService.getExpirationSeconds()
            );

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException ex) {
            // BadCredentialsException is thrown by DaoAuthenticationProvider when:
            //   - the username does not exist (converted from UsernameNotFoundException)
            //   - the password does not match the stored BCrypt hash
            // We catch it here and return 401 Unauthorized with an error message.
            // Note: Spring Security intentionally maps both cases to the same
            // exception to avoid leaking whether a username exists.
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }
}
