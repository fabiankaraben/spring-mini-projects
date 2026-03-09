package com.example.rolebasedaccess.security;

import com.example.rolebasedaccess.service.JwtService;
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
 * A Spring Security filter that intercepts every HTTP request and validates
 * the JWT carried in the {@code Authorization: Bearer <token>} header.
 *
 * <h2>Why extend OncePerRequestFilter?</h2>
 * <p>{@link OncePerRequestFilter} guarantees that {@link #doFilterInternal} is
 * executed <em>exactly once</em> per request, even when the request passes
 * through multiple servlet dispatches (e.g. error forwarding).</p>
 *
 * <h2>Role claim optimisation</h2>
 * <p>This filter reads the {@code role} claim directly from the JWT instead of
 * performing a database round-trip. This means the user's role at the time of
 * login is embedded in the token and restored from it on every subsequent request.
 * The {@code @PreAuthorize} annotation then evaluates against the restored authority.</p>
 *
 * <h2>Full validation pipeline</h2>
 * <ol>
 *   <li>Extract the {@code Authorization} header.</li>
 *   <li>Check it starts with {@code "Bearer "}; skip if not.</li>
 *   <li>Parse the username and role from the JWT (signature verified implicitly).</li>
 *   <li>Check no authentication is already present in the {@code SecurityContext}.</li>
 *   <li>Load the full {@link UserDetails} from the database (verifies user still exists).</li>
 *   <li>Validate the token (signature + expiry + subject match).</li>
 *   <li>Build a {@link UsernamePasswordAuthenticationToken} with the role from the JWT
 *       and store it in the {@link SecurityContextHolder}.</li>
 *   <li>Continue the filter chain regardless of outcome.</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Service that handles all JWT parsing, verification, and generation. */
    private final JwtService jwtService;

    /**
     * Service that loads a {@link UserDetails} object from the database.
     * Used to confirm the user still exists and to build the authentication token.
     */
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Core filter logic executed once per request.
     *
     * @param request     the incoming HTTP request
     * @param response    the outgoing HTTP response
     * @param filterChain the remaining filters in the security chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // ── Step 1: Extract the Authorization header ──────────────────────────
        final String authHeader = request.getHeader("Authorization");

        // ── Step 2: Skip if header is missing or does not start with "Bearer " ──
        // Public endpoints (e.g. /api/auth/login) will not have a Bearer token.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 3: Extract the raw JWT string (everything after "Bearer ") ──
        final String jwt = authHeader.substring(7);

        // ── Step 4: Extract username and role from the JWT ────────────────────
        // extractUsername() calls Jwts.parser().verifyWith(key).parseSignedClaims()
        // internally, which verifies the HMAC-SHA256 signature as a side-effect.
        final String username;
        final String roleFromToken;
        try {
            username = jwtService.extractUsername(jwt);
            // Extract the role claim embedded at token-generation time.
            // This avoids a DB round-trip just to get the user's role.
            roleFromToken = jwtService.extractRole(jwt);
        } catch (Exception ex) {
            // Malformed token or invalid signature – pass on without authenticating.
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 5: Proceed only if username was extracted and no auth exists ──
        if (username != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            // ── Step 6: Load the full UserDetails from the database ──────────
            // Confirms the user still exists; prevents deleted users from using
            // their old token until it naturally expires.
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // ── Step 7: Validate the token ───────────────────────────────────
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // ── Step 8: Determine authorities ────────────────────────────
                // Use the role from the JWT claim rather than re-loading from the
                // database. This keeps the flow stateless and fast.
                // Fall back to the database authorities if the claim is absent.
                List<SimpleGrantedAuthority> authorities;
                if (roleFromToken != null && !roleFromToken.isBlank()) {
                    // Role was embedded in the token at login time – use it directly.
                    authorities = List.of(new SimpleGrantedAuthority(roleFromToken));
                } else {
                    // Fallback: use whatever the UserDetailsService loaded.
                    authorities = userDetails.getAuthorities().stream()
                            .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                            .toList();
                }

                // ── Step 9: Build the authentication token ────────────────────
                // UsernamePasswordAuthenticationToken(principal, credentials, authorities)
                // Passing authorities sets isAuthenticated() = true.
                // The authorities list here is what @PreAuthorize evaluates against.
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,          // credentials are null after authentication
                                authorities
                        );

                // Attach request-level details (remote IP, session ID) for auditing.
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // ── Step 10: Store in SecurityContext ─────────────────────────
                // This is the key step: once the authentication is set here,
                // @PreAuthorize on any subsequent controller/service method will
                // evaluate the role from this authentication object.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // ── Step 11: Always continue the filter chain ─────────────────────────
        // Do NOT stop the chain on failure – Spring Security's downstream
        // ExceptionTranslationFilter handles the 401/403 response.
        filterChain.doFilter(request, response);
    }
}
