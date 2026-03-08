package com.example.jwtvalidation.security;

import com.example.jwtvalidation.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The central piece of this project: a Spring Security filter that intercepts
 * every HTTP request and validates the JWT carried in the
 * {@code Authorization: Bearer <token>} header.
 *
 * <h2>Why extend OncePerRequestFilter?</h2>
 * <p>{@link OncePerRequestFilter} guarantees that {@link #doFilterInternal} is
 * executed <em>exactly once</em> per request, even when the request passes
 * through multiple servlet dispatches (e.g. error forwarding). Without this
 * guarantee the filter could run twice, double-authenticating the request and
 * wasting CPU on signature verification.</p>
 *
 * <h2>Filter placement in the chain</h2>
 * <p>In {@code SecurityConfig} this filter is registered with
 * {@code .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)}.
 * This means it runs <em>before</em> Spring Security's built-in
 * {@code UsernamePasswordAuthenticationFilter}, ensuring that a valid JWT is
 * recognised as authentication before the form-login filter gets a chance to
 * redirect the request.</p>
 *
 * <h2>Full validation pipeline (for every incoming request)</h2>
 * <ol>
 *   <li>Extract the {@code Authorization} header.</li>
 *   <li>Check it starts with {@code "Bearer "}; skip if not (public endpoints).</li>
 *   <li>Parse the username from the JWT {@code sub} claim via
 *       {@link JwtService#extractUsername(String)} – this also verifies the
 *       signature as a side-effect.</li>
 *   <li>Check that no authentication is already present in the
 *       {@code SecurityContext} (avoid double-authenticating).</li>
 *   <li>Load the full {@link UserDetails} from the database.</li>
 *   <li>Ask {@link JwtService#isTokenValid(String, UserDetails)} to perform
 *       the full token validation (signature + expiry + subject match).</li>
 *   <li>If valid, build a {@link UsernamePasswordAuthenticationToken} and
 *       store it in the {@link SecurityContextHolder}.</li>
 *   <li>Continue the filter chain regardless of outcome; protected endpoints
 *       will be blocked by the authorisation rules if the context is empty.</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Service that handles all JWT parsing, verification, and generation. */
    private final JwtService jwtService;

    /**
     * Service that loads a {@link UserDetails} object from the database
     * given a username extracted from the JWT.
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
     * <p>The method follows the pipeline described in the class-level Javadoc.
     * It is intentionally verbose and commented for educational clarity.</p>
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
        // The standard HTTP header for bearer tokens is:
        //   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
        final String authHeader = request.getHeader("Authorization");

        // ── Step 2: Skip if header is missing or malformed ────────────────────
        // If there is no Authorization header, or it does not start with "Bearer ",
        // this is a public request (e.g. POST /api/auth/login). We skip JWT
        // processing entirely and pass the request on to the next filter.
        // The authorisation rules in SecurityConfig will block it if the
        // endpoint requires authentication.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 3: Extract the raw JWT string ────────────────────────────────
        // The token comes after the "Bearer " prefix (7 characters).
        final String jwt = authHeader.substring(7);

        // ── Step 4: Extract the username from the JWT ─────────────────────────
        // JwtService.extractUsername() calls Jwts.parser().verifyWith(key).parseSignedClaims()
        // internally, which verifies the HMAC-SHA256 signature as a side-effect.
        // If the signature is invalid or the token is malformed, a JwtException
        // is thrown. We catch it here to skip authentication instead of crashing.
        final String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception ex) {
            // Malformed token or invalid signature – continue without authenticating.
            // The downstream authorisation check will return 401 for protected endpoints.
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 5: Proceed only if username was extracted and no auth exists ──
        // getAuthentication() returns null when the current request is not yet
        // authenticated. We check this to avoid redundant work if the user was
        // already authenticated by a previous filter (rare but possible).
        if (username != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            // ── Step 6: Load the full UserDetails from the database ──────────
            // We need the stored password hash and authorities to build the
            // Authentication token. The database lookup also verifies the user
            // still exists (a user could be deleted after their token was issued).
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // ── Step 7: Validate the token against the loaded UserDetails ─────
            // This performs three checks:
            //   a) Signature correctness (already done by extractUsername, but
            //      isTokenValid re-parses to be safe with the same key)
            //   b) Token expiry – exp claim must be in the future
            //   c) Subject match – sub claim must equal userDetails.getUsername()
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // ── Step 8: Build the authentication token ────────────────────
                // UsernamePasswordAuthenticationToken(principal, credentials, authorities)
                //   - principal    → the UserDetails object (who is authenticated)
                //   - credentials  → null (we don't need the password anymore)
                //   - authorities  → the list of GrantedAuthority objects (roles)
                // Passing authorities to the constructor sets isAuthenticated() = true.
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,          // credentials are null after authentication
                                userDetails.getAuthorities()
                        );

                // Attach request-level details (remote IP, session ID) to the token.
                // This information is used by Spring Security's audit and event system.
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // ── Step 9: Store the authentication in the SecurityContext ────
                // Setting authentication on the SecurityContextHolder tells
                // Spring Security that this request is authenticated. All
                // subsequent security checks (e.g. @PreAuthorize, hasRole())
                // will see this authentication object.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
            // If isTokenValid returns false, we simply do not set any
            // authentication. The SecurityContext remains empty and protected
            // endpoints will respond with 401 Unauthorized.
        }

        // ── Step 10: Continue the filter chain ────────────────────────────────
        // IMPORTANT: always call doFilter, even if authentication failed.
        // Stopping the chain here would prevent error responses from being
        // sent back to the client. Spring Security's ExceptionTranslationFilter
        // (downstream) handles the 401 response when authentication is missing.
        filterChain.doFilter(request, response);
    }
}
