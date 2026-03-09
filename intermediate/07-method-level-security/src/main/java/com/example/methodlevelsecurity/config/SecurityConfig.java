package com.example.methodlevelsecurity.config;

import com.example.methodlevelsecurity.security.JwtAuthenticationFilter;
import com.example.methodlevelsecurity.security.UserDetailsServiceImpl;
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
 * Spring Security configuration for the Method Level Security mini-project.
 *
 * <h2>The two layers of access control in this project</h2>
 *
 * <h3>Layer 1 – URL-level security (this class)</h3>
 * <p>Configured via {@code http.authorizeHttpRequests(...)}. This is coarse-grained:
 * it permits or denies entire URL namespaces before the request reaches any controller.</p>
 *
 * <h3>Layer 2 – Method-level security (service and controller methods)</h3>
 * <p>Enabled by the {@code @EnableMethodSecurity} annotation on this class.
 * Spring Security wraps annotated beans in a CGLIB proxy. When a secured method
 * is called, the proxy evaluates the SpEL expression against the current
 * {@link org.springframework.security.core.Authentication} in the
 * {@link org.springframework.security.core.context.SecurityContextHolder}
 * <em>before</em> (or after) delegating to the real method.</p>
 *
 * <h2>Annotations enabled by @EnableMethodSecurity</h2>
 * <ul>
 *   <li>{@code @PreAuthorize}  – SpEL check BEFORE the method runs (most common).</li>
 *   <li>{@code @PostAuthorize} – SpEL check AFTER the method returns; can inspect
 *       {@code returnObject} in the expression.</li>
 *   <li>{@code @PreFilter}     – filters a collection <em>parameter</em> before execution.</li>
 *   <li>{@code @PostFilter}    – filters a collection <em>return value</em> after execution.</li>
 *   <li>{@code @Secured}       – simpler role-only alternative to {@code @PreAuthorize};
 *       enabled via {@code securedEnabled = true}.</li>
 * </ul>
 *
 * <h2>Why @EnableMethodSecurity and not @EnableGlobalMethodSecurity?</h2>
 * <p>{@code @EnableGlobalMethodSecurity} is deprecated since Spring Security 5.6.
 * {@code @EnableMethodSecurity} is its modern replacement and enables
 * {@code prePostEnabled} (i.e. {@code @PreAuthorize} / {@code @PostAuthorize}) by default.
 * {@code securedEnabled = true} additionally activates the older {@code @Secured}.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)   // enables @PreAuthorize, @PostAuthorize,
                                                // @PreFilter, @PostFilter, AND @Secured
public class SecurityConfig {

    /** Custom UserDetails loader – looks up users from PostgreSQL via JPA. */
    private final UserDetailsServiceImpl userDetailsService;

    /** JWT filter – inserted before the standard username/password filter. */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Defines the main HTTP security filter chain.
     *
     * <p>URL-level rules (Layer 1):</p>
     * <ul>
     *   <li>{@code /api/auth/**} – publicly accessible (login and register).</li>
     *   <li>{@code /api/**}      – all other API endpoints require authentication.</li>
     * </ul>
     *
     * <p>Note: individual endpoint permissions are further refined by method-level
     * annotations on the service and controller classes (Layer 2).</p>
     *
     * @param http the {@link HttpSecurity} builder provided by Spring
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF – REST APIs using Bearer tokens are not susceptible
                // to CSRF attacks since attackers cannot read or inject the token
                .csrf(AbstractHttpConfigurer::disable)

                // ── URL-level rules (Layer 1) ─────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints are public – no token needed to login or register
                        .requestMatchers("/api/auth/**").permitAll()
                        // All other API requests require a valid JWT (any role)
                        .anyRequest().authenticated()
                )

                // Stateless sessions: every request is independently authenticated via JWT.
                // No HttpSession is ever created or used.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Return 401 Unauthorized (not 302 redirect) when credentials are missing.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(
                                        jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED,
                                        authException.getMessage())
                        )
                )

                // Register the DaoAuthenticationProvider to validate username/password
                .authenticationProvider(authenticationProvider())

                // Insert the JWT filter BEFORE UsernamePasswordAuthenticationFilter so
                // that the JWT is parsed and the SecurityContext is populated before
                // any other authentication mechanism is attempted.
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder bean.
     *
     * <p>BCrypt is recommended because it is adaptive (cost factor can be raised over
     * time) and automatically generates a unique random salt per hash.</p>
     *
     * @return a {@link BCryptPasswordEncoder} with the default cost factor (10)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * {@link AuthenticationProvider} that loads users from PostgreSQL via
     * {@link UserDetailsServiceImpl} and verifies passwords with BCrypt.
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
     * {@link com.example.methodlevelsecurity.controller.AuthController} can inject
     * it to authenticate login requests programmatically.
     *
     * @param config auto-configured by Spring from the registered providers
     * @return the global {@link AuthenticationManager}
     * @throws Exception if retrieval fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
