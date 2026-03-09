package com.example.oauth2loginclient.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the OAuth2 Login Client.
 *
 * <p>This class replaces the older {@code WebSecurityConfigurerAdapter} approach
 * (deprecated since Spring Security 5.7 / Spring Boot 3.x) with the modern
 * component-based {@link SecurityFilterChain} bean pattern.</p>
 *
 * <h2>Access rules</h2>
 * <ul>
 *   <li>{@code GET /} – publicly accessible welcome endpoint</li>
 *   <li>{@code GET /api/users/**} – requires authentication (any logged-in user)</li>
 *   <li>All other requests – requires authentication</li>
 * </ul>
 *
 * <h2>OAuth2 login</h2>
 * Spring Security automatically registers:
 * <ul>
 *   <li>{@code GET /oauth2/authorization/{registrationId}} – redirects the
 *       browser to the provider's authorization page</li>
 *   <li>{@code GET /login/oauth2/code/{registrationId}} – the redirect URI
 *       (callback) where the provider returns the authorization code</li>
 * </ul>
 * Both endpoints are handled transparently by the
 * {@code OAuth2LoginAuthenticationFilter} in the filter chain.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Custom OAuth2 user service that persists user profiles after login. */
    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    /**
     * Defines the security filter chain that governs how HTTP requests are
     * authenticated and authorized.
     *
     * @param http the {@link HttpSecurity} builder provided by Spring Security
     * @return the configured {@link SecurityFilterChain} bean
     * @throws Exception if any configuration method throws (Spring Security API)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Authorization rules ──────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // The root endpoint is public so users can see the welcome message
                // before they have logged in.
                .requestMatchers("/").permitAll()
                // H2 console is accessible during tests (not exposed in prod)
                .requestMatchers("/h2-console/**").permitAll()
                // All API endpoints require the user to be authenticated
                .requestMatchers("/api/**").authenticated()
                // Any other request also requires authentication
                .anyRequest().authenticated()
            )
            // ── OAuth2 login configuration ───────────────────────────────────
            .oauth2Login(oauth2 -> oauth2
                // Register our custom user service so user profiles are
                // persisted to the database on every successful login
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                // After a successful login, redirect to the /api/me endpoint
                // so the browser/client immediately sees the authenticated profile
                .defaultSuccessUrl("/api/me", true)
            )
            // ── Logout configuration ─────────────────────────────────────────
            .logout(logout -> logout
                // Invalidate the HTTP session and clear the SecurityContext on logout
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            )
            // ── CSRF ─────────────────────────────────────────────────────────
            // CSRF protection is enabled by default for browser-based flows.
            // For a REST-only API you would disable it, but since this project
            // uses session-based OAuth2 login it should remain enabled.
            // H2 console requires frame options to be relaxed (test only)
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            );

        return http.build();
    }
}
