package com.example.methodlevelsecurity.security;

import com.example.methodlevelsecurity.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that validates the JWT on every incoming HTTP request.
 *
 * <h2>Why OncePerRequestFilter?</h2>
 * <p>{@link OncePerRequestFilter} guarantees that {@link #doFilterInternal} is
 * called <em>exactly once</em> per request dispatched to the servlet, even when
 * the request is forwarded internally (e.g. to the error endpoint).</p>
 *
 * <h2>Where does the Authentication end up?</h2>
 * <p>After a valid JWT is verified, a {@link UsernamePasswordAuthenticationToken}
 * is stored in the {@link SecurityContextHolder}. This is the object that
 * {@code @PreAuthorize}, {@code @PostAuthorize}, etc. evaluate. The principal
 * name ({@code authentication.name}) and the granted authorities
 * ({@code authentication.authorities}) come from this token.</p>
 *
 * <h2>Role claim optimisation</h2>
 * <p>The role is embedded in the JWT at login time. This filter reads it from
 * the token directly, avoiding an extra database query on every request.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT creation, parsing, and validation logic. */
    private final JwtService jwtService;

    /**
     * Loads the full UserDetails from the database.
     * Used to confirm the user still exists (prevents stale tokens for deleted users).
     */
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Core JWT validation logic, executed once per request.
     *
     * <p>Pipeline:</p>
     * <ol>
     *   <li>Read the {@code Authorization} header.</li>
     *   <li>Skip if missing or not a Bearer token.</li>
     *   <li>Extract username and role claim from the token (signature verified implicitly).</li>
     *   <li>Skip if SecurityContext already has an Authentication (request already processed).</li>
     *   <li>Load UserDetails from DB (ensures user still exists).</li>
     *   <li>Validate the token (expiry, subject match).</li>
     *   <li>Build an {@link UsernamePasswordAuthenticationToken} with the role and store it.</li>
     *   <li>Always continue the filter chain.</li>
     * </ol>
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // ── Step 1: Read the Authorization header ─────────────────────────────
        final String authHeader = request.getHeader("Authorization");

        // ── Step 2: Skip if the header is absent or not a Bearer token ────────
        // Public endpoints (login, register) will reach here without a token.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 3: Extract the JWT string (everything after "Bearer ") ───────
        final String jwt = authHeader.substring(7);

        // ── Step 4: Parse the JWT – signature is verified as a side-effect ────
        final String username;
        final String roleFromToken;
        try {
            username = jwtService.extractUsername(jwt);
            roleFromToken = jwtService.extractRole(jwt);
        } catch (Exception ex) {
            // Malformed or tampered token – pass through without authenticating.
            // Spring Security's ExceptionTranslationFilter will return 401.
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 5: Authenticate only if not already done ─────────────────────
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // ── Step 6: Load UserDetails to confirm the user still exists ─────
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // ── Step 7: Validate expiry and subject match ─────────────────────
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // ── Step 8: Build granted authorities from the JWT role claim ──
                // Using the claim avoids another DB query for the role.
                List<SimpleGrantedAuthority> authorities;
                if (roleFromToken != null && !roleFromToken.isBlank()) {
                    // Role embedded in the token at login – use it directly.
                    // This is what @PreAuthorize("hasRole('ADMIN')") evaluates.
                    authorities = List.of(new SimpleGrantedAuthority(roleFromToken));
                } else {
                    // Fallback: re-use the authorities loaded from the database.
                    authorities = userDetails.getAuthorities().stream()
                            .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                            .toList();
                }

                // ── Step 9: Create the Spring Security Authentication token ───
                // Passing authorities into the constructor sets isAuthenticated() = true.
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,       // credentials are null after authentication
                                authorities
                        );

                // Attach HTTP request details (IP, session ID) for audit purposes.
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // ── Step 10: Store in SecurityContext ─────────────────────────
                // This is the key moment: once this is stored, any @PreAuthorize,
                // @PostAuthorize, or @Secured annotation on service/controller methods
                // will evaluate the expression against this authentication object.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // ── Step 11: Always continue the filter chain ─────────────────────────
        filterChain.doFilter(request, response);
    }
}
