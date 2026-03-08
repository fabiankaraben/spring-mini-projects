package com.example.jwtvalidation.config;

import com.example.jwtvalidation.security.JwtAuthenticationFilter;
import com.example.jwtvalidation.security.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the JWT Validation project.
 *
 * <h2>Key difference from 03-jwt-generation</h2>
 * <p>The previous project ({@code 03-jwt-generation}) issued JWTs but did not
 * validate them on subsequent requests – protected endpoints were essentially
 * accessible to anyone who knew the URL. This project adds the
 * {@link JwtAuthenticationFilter} to the security filter chain, which means
 * every request to a protected endpoint is now validated against the JWT before
 * it reaches the controller.</p>
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><strong>Stateless sessions</strong> – we set the session policy to
 *       {@code STATELESS} because JWTs are self-contained: the server does
 *       not need to maintain any session state between requests. Each request
 *       must carry its own token.</li>
 *   <li><strong>CSRF disabled</strong> – CSRF protection is only meaningful
 *       for browser-based session cookies. REST APIs using Bearer tokens are
 *       not susceptible to CSRF attacks, so we disable it to avoid 403 errors
 *       on POST requests from tools like {@code curl}.</li>
 *   <li><strong>JWT filter before username/password filter</strong> – by calling
 *       {@code addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)},
 *       the JWT filter runs first and populates the {@code SecurityContext}
 *       before the standard form-login filter gets a chance to interfere.</li>
 * </ul>
 *
 * <h2>Request flow for a protected endpoint</h2>
 * <pre>
 *   Client request
 *       ↓
 *   JwtAuthenticationFilter   ← extracts + validates the Bearer token
 *       ↓ (if valid, sets Authentication in SecurityContextHolder)
 *   UsernamePasswordAuthenticationFilter  ← skipped (no username/password form)
 *       ↓
 *   ExceptionTranslationFilter ← catches AccessDeniedException / AuthenticationException
 *       ↓
 *   FilterSecurityInterceptor  ← checks authorisation rules (hasRole, etc.)
 *       ↓
 *   DispatcherServlet / Controller
 * </pre>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    /**
     * The custom JWT filter injected here so it can be added to the chain.
     * Spring creates this bean via {@code @Component} on {@link JwtAuthenticationFilter}.
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Defines the main HTTP security filter chain.
     *
     * <p>Rules applied:</p>
     * <ul>
     *   <li>{@code /api/auth/**} – publicly accessible (login and register endpoints).</li>
     *   <li>{@code /api/protected/admin} – requires {@code ROLE_ADMIN}.</li>
     *   <li>{@code /api/protected/**} – requires any authenticated user.</li>
     *   <li>All other requests – require authentication by default.</li>
     * </ul>
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

                // Configure URL-level access rules
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints (login, register) must be publicly accessible
                        .requestMatchers("/api/auth/**").permitAll()
                        // Admin-only endpoint – demonstrates role-based access via JWT claims
                        .requestMatchers("/api/protected/admin").hasRole("ADMIN")
                        // All other requests require a valid, authenticated JWT
                        .anyRequest().authenticated()
                )

                // Use STATELESS session policy – Spring Security will not create an
                // HttpSession; each request must carry its own authentication token.
                // This is critical for JWT-based auth to work correctly.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Override the default authentication entry point so that requests
                // with no (or invalid) credentials receive 401 Unauthorized instead
                // of Spring Security's default 403 Forbidden. RFC 7235 requires 401
                // when credentials are missing/invalid and 403 when they are present
                // but insufficient for the requested resource.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(
                                        jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED,
                                        authException.getMessage())
                        )
                )

                // Register our custom DaoAuthenticationProvider so Spring Security
                // knows how to load users and verify passwords on login
                .authenticationProvider(authenticationProvider())

                // ── THE KEY ADDITION IN THIS PROJECT ──────────────────────────
                // Insert the JWT filter BEFORE the standard username/password filter.
                // This ensures the JWT is parsed and the SecurityContext is populated
                // before any other authentication mechanism is attempted.
                // If the JWT is valid, the request is authenticated here, and the
                // UsernamePasswordAuthenticationFilter is effectively skipped.
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * {@link PasswordEncoder} bean using BCrypt.
     *
     * <p>BCrypt is the recommended choice because:</p>
     * <ul>
     *   <li>It is adaptive – the cost factor (work factor) can be increased to
     *       keep pace with faster hardware.</li>
     *   <li>It auto-generates a random salt and embeds it in the hash, so
     *       identical passwords produce different hashes.</li>
     * </ul>
     *
     * @return a {@link BCryptPasswordEncoder} with default cost (10)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * {@link AuthenticationProvider} that uses our {@link UserDetailsServiceImpl}
     * to load users from PostgreSQL and {@link BCryptPasswordEncoder} to verify
     * submitted passwords against stored hashes.
     *
     * <p>{@code DaoAuthenticationProvider} is the standard Spring Security
     * implementation for database-backed authentication.</p>
     *
     * @return a configured {@link DaoAuthenticationProvider}
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        // Tell the provider where to find users
        provider.setUserDetailsService(userDetailsService);
        // Tell the provider how to verify passwords
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the {@link AuthenticationManager} as a Spring bean so that the
     * {@code AuthController} can inject and use it directly to authenticate
     * login requests.
     *
     * <p>Without this bean definition, the {@code AuthenticationManager} would
     * only be available internally within the security filter chain.</p>
     *
     * @param config auto-configured by Spring from the {@link AuthenticationProvider}
     *               registered above
     * @return the global {@link AuthenticationManager}
     * @throws Exception if retrieval fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
