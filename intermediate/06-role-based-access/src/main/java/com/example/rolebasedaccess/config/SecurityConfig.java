package com.example.rolebasedaccess.config;

import com.example.rolebasedaccess.security.JwtAuthenticationFilter;
import com.example.rolebasedaccess.security.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the Role-Based Access mini-project.
 *
 * <h2>The two layers of access control in this project</h2>
 *
 * <h3>Layer 1 – URL-level security (this class)</h3>
 * <p>Configured via {@code http.authorizeHttpRequests(...)}, this layer checks
 * access rules based on URL patterns <em>before</em> the request reaches any
 * controller. It is coarse-grained: good for protecting entire API namespaces.</p>
 *
 * <h3>Layer 2 – Method-level security ({@code @PreAuthorize})</h3>
 * <p>Enabled by the <strong>{@code @EnableMethodSecurity}</strong> annotation on
 * this class. This allows {@code @PreAuthorize("hasRole('ADMIN')")} (and similar
 * SpEL expressions) to be placed directly on service or controller methods.
 * Spring Security wraps the annotated beans in a CGLIB proxy; when the method is
 * called the proxy evaluates the SpEL expression against the current
 * {@code Authentication} in the {@code SecurityContextHolder} <em>before</em>
 * delegating to the real method. If the expression returns {@code false}, an
 * {@link org.springframework.security.access.AccessDeniedException} is thrown.</p>
 *
 * <h2>Why @EnableMethodSecurity and not @EnableGlobalMethodSecurity?</h2>
 * <p>{@code @EnableGlobalMethodSecurity} is deprecated since Spring Security 5.6.
 * {@code @EnableMethodSecurity} is its modern replacement and enables
 * {@code prePostEnabled} by default.</p>
 *
 * <h2>Session policy</h2>
 * <p>Sessions are {@code STATELESS}: each request is authenticated independently
 * via its JWT. The server never stores session state.</p>
 *
 * <h2>CSRF</h2>
 * <p>CSRF protection is disabled because REST APIs using Bearer tokens are not
 * susceptible to CSRF attacks (a CSRF attacker cannot read or inject the token
 * from a different origin).</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // Activates @PreAuthorize, @PostAuthorize, @Secured, etc.
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    /** Custom JWT filter – registered in the chain below. */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Defines the main HTTP security filter chain.
     *
     * <h2>URL-level rules</h2>
     * <ul>
     *   <li>{@code /api/auth/**} – publicly accessible (login and register).</li>
     *   <li>{@code /api/admin/**} – URL-level restriction to {@code ROLE_ADMIN}
     *       (in addition to the {@code @PreAuthorize} on individual methods).</li>
     *   <li>{@code /api/**} – any other API endpoint requires authentication.</li>
     *   <li>Everything else – also requires authentication by default.</li>
     * </ul>
     *
     * <p><strong>Note on double protection:</strong> the admin endpoints are
     * protected at two levels. If you remove the URL-level rule, the
     * {@code @PreAuthorize} on the service methods still enforces access. The
     * URL-level rule acts as a first line of defence and can produce a 403 even
     * before the controller is instantiated.</p>
     *
     * @param http the {@link HttpSecurity} builder provided by Spring
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if the configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF – not needed for stateless REST APIs using Bearer tokens
                .csrf(AbstractHttpConfigurer::disable)

                // ── URL-level access rules (Layer 1) ─────────────────────────
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints must be publicly accessible (no token required)
                        .requestMatchers("/api/auth/**").permitAll()
                        // Admin namespace – coarse-grained URL protection
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // All other API requests require a valid, authenticated JWT
                        .anyRequest().authenticated()
                )

                // Use STATELESS session policy – no HttpSession is ever created.
                // Every request carries its own JWT credential.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Return 401 Unauthorized (not 403) when credentials are missing.
                // RFC 7235 requires 401 when credentials are absent/invalid.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(
                                        jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED,
                                        authException.getMessage())
                        )
                )

                // Register our DaoAuthenticationProvider so Spring Security knows
                // how to load users and verify BCrypt passwords on login.
                .authenticationProvider(authenticationProvider())

                // Insert the JWT filter BEFORE the standard UsernamePasswordAuthenticationFilter.
                // This ensures the JWT is parsed and the SecurityContext populated
                // before any other authentication mechanism is attempted.
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * {@link PasswordEncoder} bean using BCrypt.
     *
     * <p>BCrypt is the recommended choice because:</p>
     * <ul>
     *   <li>It is adaptive – the cost factor can be increased over time.</li>
     *   <li>It auto-generates a random salt embedded in the hash, so identical
     *       passwords produce different hashes on each call.</li>
     * </ul>
     *
     * @return a {@link BCryptPasswordEncoder} with default cost factor (10)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * {@link AuthenticationProvider} that uses {@link UserDetailsServiceImpl}
     * to load users from PostgreSQL and {@link BCryptPasswordEncoder} to verify
     * submitted passwords against stored hashes.
     *
     * @return a configured {@link DaoAuthenticationProvider}
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the {@link AuthenticationManager} as a Spring bean so that the
     * {@code AuthController} can inject and use it directly to authenticate
     * login requests programmatically.
     *
     * @param config auto-configured by Spring from the registered {@link AuthenticationProvider}
     * @return the global {@link AuthenticationManager}
     * @throws Exception if retrieval fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
