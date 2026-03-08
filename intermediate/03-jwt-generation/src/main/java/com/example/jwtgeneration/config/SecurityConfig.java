package com.example.jwtgeneration.config;

import com.example.jwtgeneration.security.UserDetailsServiceImpl;
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

/**
 * Spring Security configuration for the JWT Generation project.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><strong>Stateless sessions</strong> – we set the session policy to
 *       {@code STATELESS} because JWTs are self-contained: the server does
 *       not need to maintain any session state between requests.</li>
 *   <li><strong>CSRF disabled</strong> – CSRF protection is only meaningful
 *       for browser-based session cookies. REST APIs using Bearer tokens are
 *       not susceptible to CSRF attacks, so we disable it to avoid 403 errors
 *       on POST requests from tools like {@code curl}.</li>
 *   <li><strong>No JWT filter yet</strong> – this project focuses solely on
 *       <em>generating</em> JWTs. A JWT validation filter (which would parse
 *       the {@code Authorization} header on every request) is intentionally
 *       omitted here and is the subject of the next mini-project
 *       {@code 04-jwt-validation}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * Defines the main HTTP security filter chain.
     *
     * <p>Rules applied:
     * <ul>
     *   <li>{@code /api/auth/**} – publicly accessible (login and register endpoints).</li>
     *   <li>{@code /api/protected/**} – requires authentication (demonstrates that
     *       the JWT works by providing a protected endpoint to call with the token).</li>
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
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // Use STATELESS session policy – Spring Security will not create an
                // HttpSession; each request must carry its own authentication token
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Register our custom DaoAuthenticationProvider so Spring Security
                // knows how to load users and verify passwords
                .authenticationProvider(authenticationProvider());

        return http.build();
    }

    /**
     * {@link PasswordEncoder} bean using BCrypt.
     *
     * <p>BCrypt is the recommended choice because:
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
     * implementation for database-backed authentication.
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
     * only be available internally within the security filter chain.
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
