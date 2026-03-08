package com.example.formlogin.config;

import com.example.formlogin.security.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

/**
 * Central Spring Security configuration for the form login flow.
 *
 * <p><strong>How form login works:</strong>
 * <ol>
 *   <li>User visits a protected URL (e.g. {@code /dashboard}).</li>
 *   <li>Spring Security detects no active session and redirects to {@code /login}.</li>
 *   <li>The browser renders the HTML login form (served by
 *       {@link com.example.formlogin.controller.PageController}).</li>
 *   <li>User submits username + password via HTTP POST to {@code /login}.</li>
 *   <li>Spring Security authenticates the credentials using
 *       {@link UserDetailsServiceImpl} and BCrypt password comparison.</li>
 *   <li>On success, a new session is created and the user is redirected to
 *       {@code /dashboard}.</li>
 *   <li>On failure, the user is redirected back to {@code /login?error}.</li>
 *   <li>On logout (POST to {@code /logout}), the session is invalidated, cookies
 *       are cleared, and the user is redirected to {@code /login?logout}.</li>
 * </ol>
 *
 * <p><strong>CSRF protection:</strong> Unlike REST APIs that disable CSRF, form-based
 * applications keep CSRF enabled (the default). Thymeleaf automatically injects the
 * hidden {@code _csrf} token into every form using the
 * {@code th:action="@{/login}"} syntax.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * Defines the security filter chain – the heart of this configuration.
     *
     * <p>CSRF is intentionally left <em>enabled</em> here because this is a
     * browser-based application. Thymeleaf's {@code th:action} directive
     * injects the CSRF token into every form automatically.
     *
     * @param http the {@link HttpSecurity} builder provided by Spring Security
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Authorization rules ──────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // The login page itself must be public so unauthenticated users
                // can access it; otherwise they'd get an infinite redirect loop.
                .requestMatchers("/login", "/css/**", "/js/**").permitAll()
                // Only users with ROLE_ADMIN may access admin pages
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Every other URL requires the user to be authenticated
                .anyRequest().authenticated()
            )

            // ── Form login configuration ──────────────────────────────────────
            .formLogin(form -> form
                // Tell Spring Security where our custom login page lives.
                // Without this, Spring would generate its own default form.
                .loginPage("/login")
                // The URL that the <form> POSTs credentials to.
                // Spring Security handles this POST internally – no controller needed.
                .loginProcessingUrl("/login")
                // Where to send the browser after a successful login.
                // true = always redirect here, even if there was no originally
                // requested URL saved in the session.
                .defaultSuccessUrl("/dashboard", true)
                // Where to send the browser after a failed login attempt.
                // The ?error query param lets the login page display an error message.
                .failureUrl("/login?error")
                // Permit everyone to reach the login page and its POST endpoint
                .permitAll()
            )

            // ── Logout configuration ──────────────────────────────────────────
            .logout(logout -> logout
                // The URL that triggers logout (submitted as a POST form).
                // Using POST prevents accidental/CSRF-triggered logouts via GET links.
                .logoutUrl("/logout")
                // Where to redirect after a successful logout.
                // The ?logout query param lets the login page show a "logged out" notice.
                .logoutSuccessUrl("/login?logout")
                // Destroy the HTTP session to remove all session-bound data.
                .invalidateHttpSession(true)
                // Remove the "remember me" cookie if present
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }

    /**
     * BCrypt password encoder bean.
     *
     * <p>BCrypt is a strong adaptive hashing algorithm suitable for passwords.
     * The same encoder instance is used both when saving users (in
     * {@link DataInitializer}) and when verifying passwords during login.
     *
     * @return a {@link BCryptPasswordEncoder} with default strength (10 rounds)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Wires the custom {@link UserDetailsServiceImpl} and {@link PasswordEncoder}
     * into the authentication manager used by Spring Security.
     *
     * <p>{@link DaoAuthenticationProvider} is the standard provider for
     * database-backed authentication: it calls {@code loadUserByUsername()},
     * then compares the raw password from the login form with the stored hash
     * using the injected {@link PasswordEncoder}.
     *
     * @return the configured {@link AuthenticationManager}
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(authProvider);
    }

    /**
     * Custom failure handler bean (exposed for testing convenience).
     *
     * <p>Delegates to Spring's default {@link SimpleUrlAuthenticationFailureHandler}
     * pointing at {@code /login?error}. Declaring it as a bean allows integration
     * tests to reference the exact URL without hard-coding strings.
     *
     * @return the {@link AuthenticationFailureHandler}
     */
    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler("/login?error");
    }
}
