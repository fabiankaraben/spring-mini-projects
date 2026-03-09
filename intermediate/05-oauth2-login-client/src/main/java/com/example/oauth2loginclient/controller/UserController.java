package com.example.oauth2loginclient.controller;

import com.example.oauth2loginclient.domain.AppUser;
import com.example.oauth2loginclient.dto.UserProfileDto;
import com.example.oauth2loginclient.service.AppUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller that exposes user profile endpoints for authenticated users.
 *
 * <p>All endpoints in this controller require authentication; the security
 * filter chain in {@code SecurityConfig} enforces this for
 * {@code /api/**} requests.</p>
 *
 * <h2>How @AuthenticationPrincipal works</h2>
 * <p>Spring Security stores the authenticated principal in the
 * {@code SecurityContextHolder}. When the method parameter is annotated with
 * {@link AuthenticationPrincipal}, Spring MVC automatically resolves it to the
 * current principal. For an OAuth2-authenticated user the principal is the
 * {@link OAuth2User} returned by our {@code CustomOAuth2UserService}.</p>
 */
@RestController
@RequestMapping("/api")
public class UserController {

    /** Service that handles database persistence for user profiles. */
    private final AppUserService appUserService;

    public UserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    // ── Endpoints ────────────────────────────────────────────────────────────

    /**
     * Returns the profile of the currently authenticated user.
     *
     * <p>The method extracts the OAuth2 provider name and subject identifier
     * from the current {@link OAuth2User} principal, then looks up the
     * corresponding {@link AppUser} record in the database.</p>
     *
     * <p>The provider name is stored as an attribute named
     * {@code "registrationId"} that we set during the OAuth2 user loading
     * process — it is the Spring Security {@code registrationId} (e.g.
     * "github" or "google").</p>
     *
     * @param oauth2User the currently authenticated OAuth2 principal
     * @return 200 with the {@link UserProfileDto}, or 404 if no matching DB record exists
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getCurrentUser(
            @AuthenticationPrincipal OAuth2User oauth2User) {

        // Extract the provider name from the OAuth2User attributes.
        // Spring Security injects a "registrationId" attribute via our
        // CustomOAuth2UserService. For GitHub it is "github"; for Google "google".
        String provider   = extractProvider(oauth2User);
        String providerId = extractProviderId(provider, oauth2User);

        Optional<AppUser> user = appUserService.findByProviderAndProviderId(provider, providerId);

        return user
            .map(u -> ResponseEntity.ok(UserProfileDto.from(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns a list of all users that have ever logged in via OAuth2.
     *
     * <p>In a production application this endpoint would require an admin role
     * and would return a paginated response. Here it is intentionally simple
     * for educational purposes.</p>
     *
     * @return 200 with the list of all {@link UserProfileDto} objects
     */
    @GetMapping("/users")
    public List<UserProfileDto> getAllUsers() {
        return appUserService.findAllUsers()
                .stream()
                .map(UserProfileDto::from)
                .toList();
    }

    /**
     * Returns a single user's profile by their database primary key.
     *
     * @param id the surrogate primary key of the user
     * @return 200 with the {@link UserProfileDto}, or 404 if not found
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<UserProfileDto> getUserById(@PathVariable Long id) {
        return appUserService.findById(id)
                .map(u -> ResponseEntity.ok(UserProfileDto.from(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns the raw OAuth2 attributes map for the current user.
     *
     * <p>This diagnostic endpoint is useful during development and demos to
     * inspect exactly which attributes a provider returns. It should be
     * disabled or protected in production.</p>
     *
     * @param oauth2User the currently authenticated OAuth2 principal
     * @return 200 with the raw attribute map from the provider
     */
    @GetMapping("/me/attributes")
    public Map<String, Object> getCurrentUserAttributes(
            @AuthenticationPrincipal OAuth2User oauth2User) {
        // Return the raw attribute map so developers can see exactly what the
        // provider sends during the UserInfo response.
        return oauth2User.getAttributes();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Determines the OAuth2 provider name from the attributes in the principal.
     *
     * <p>We use a heuristic: GitHub responses include an {@code "avatar_url"}
     * attribute while Google responses include a {@code "sub"} attribute.
     * This avoids the need to store the registrationId as a separate attribute
     * in the OAuth2User.</p>
     */
    private String extractProvider(OAuth2User user) {
        // GitHub-specific attribute used as a discriminator
        if (user.getAttribute("avatar_url") != null) {
            return "github";
        }
        // Google OIDC uses "sub" as the subject identifier
        return "google";
    }

    /**
     * Extracts the provider's opaque user identifier from the OAuth2User
     * attribute map, applying the same provider-specific logic as
     * {@code CustomOAuth2UserService#extractProviderId}.
     */
    private String extractProviderId(String provider, OAuth2User user) {
        if ("github".equals(provider)) {
            Object id = user.getAttribute("id");
            return id != null ? String.valueOf(id) : null;
        }
        return user.getAttribute("sub");
    }
}
