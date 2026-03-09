package com.example.methodlevelsecurity.controller;

import com.example.methodlevelsecurity.domain.User;
import com.example.methodlevelsecurity.dto.LoginRequest;
import com.example.methodlevelsecurity.dto.LoginResponse;
import com.example.methodlevelsecurity.dto.RegisterRequest;
import com.example.methodlevelsecurity.service.JwtService;
import com.example.methodlevelsecurity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller handling user authentication (login) and registration.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /api/auth/register} – creates a new user account.</li>
 *   <li>{@code POST /api/auth/login}    – authenticates and returns a JWT.</li>
 * </ul>
 *
 * <p>These endpoints are publicly accessible (no JWT required) as configured
 * in {@link com.example.methodlevelsecurity.config.SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    /**
     * Spring Security's AuthenticationManager – delegates credential verification
     * to the configured {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}.
     */
    private final AuthenticationManager authenticationManager;

    public AuthController(UserService userService,
                          JwtService jwtService,
                          AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Registers a new user account.
     *
     * <p>On success, returns {@code 201 Created} with a confirmation message.
     * On duplicate username, returns {@code 409 Conflict}.</p>
     *
     * <p><b>Request body example:</b></p>
     * <pre>{@code
     * { "username": "alice", "password": "secret123", "role": "ROLE_USER" }
     * }</pre>
     *
     * @param request validated registration DTO
     * @return 201 Created with a message map, or 409 Conflict with an error map
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = userService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message",  "User registered successfully",
                    "username", user.getUsername(),
                    "role",     user.getRole().name()
            ));
        } catch (IllegalArgumentException ex) {
            // Username already taken
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Authenticates a user and returns a signed JWT.
     *
     * <p>The {@link AuthenticationManager} verifies the username and password.
     * On success, a JWT containing the username and role is generated and returned.
     * The client must include this token as {@code Authorization: Bearer <token>}
     * on all subsequent requests.</p>
     *
     * <p><b>Request body example:</b></p>
     * <pre>{@code
     * { "username": "alice", "password": "secret123" }
     * }</pre>
     *
     * <p><b>Response (200 OK):</b></p>
     * <pre>{@code
     * { "token": "eyJhbGci...", "role": "ROLE_USER" }
     * }</pre>
     *
     * @param request validated login DTO
     * @return 200 OK with the JWT, or 401 Unauthorized on bad credentials
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // Delegate credential verification to Spring Security's AuthenticationManager.
            // Internally it calls UserDetailsServiceImpl.loadUserByUsername() and then
            // BCryptPasswordEncoder.matches() to verify the password hash.
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Extract the role from the granted authorities of the authenticated principal.
            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(a -> a.getAuthority())
                    .orElse("ROLE_USER");

            // Generate a JWT containing the username (sub claim) and role (custom claim).
            String token = jwtService.generateToken(authentication.getName(), role);

            return ResponseEntity.ok(new LoginResponse(token, role));

        } catch (AuthenticationException ex) {
            // Bad credentials – return 401 Unauthorized
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }
}
