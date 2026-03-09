package com.example.oauth2loginclient.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Simple public welcome endpoint.
 *
 * <p>This controller is intentionally minimal. Its sole purpose is to provide
 * an unauthenticated landing point so that users (or automated health checks)
 * can verify the application is running before initiating the OAuth2 flow.</p>
 */
@RestController
public class HomeController {

    /**
     * Returns a welcome message with links to the GitHub and Google login URLs.
     *
     * <p>The OAuth2 authorization URLs are constructed by Spring Security's
     * {@code OAuth2AuthorizationRequestRedirectFilter}. When the browser hits
     * {@code /oauth2/authorization/github} Spring Security redirects it to
     * GitHub's authorization endpoint with the correct {@code client_id},
     * {@code redirect_uri}, {@code scope}, and {@code state} parameters.</p>
     *
     * @return a JSON map with a welcome message and login links
     */
    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
            "message",    "Welcome to the OAuth2 Login Client",
            "loginLinks", Map.of(
                "github", "/oauth2/authorization/github",
                "google", "/oauth2/authorization/google"
            ),
            "apiDocs", Map.of(
                "currentUser", "/api/me",
                "allUsers",    "/api/users"
            )
        );
    }
}
