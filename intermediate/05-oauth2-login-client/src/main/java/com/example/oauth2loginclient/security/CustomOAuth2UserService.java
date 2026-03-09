package com.example.oauth2loginclient.security;

import com.example.oauth2loginclient.domain.AppUser;
import com.example.oauth2loginclient.service.AppUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Custom {@link org.springframework.security.oauth2.client.userinfo.OAuth2UserService}
 * that extends Spring Security's default implementation to persist user
 * profiles in the local database after every successful OAuth2 login.
 *
 * <h2>OAuth2 Authorization Code flow recap</h2>
 * <ol>
 *   <li>User clicks "Login with GitHub/Google" → browser is redirected to the
 *       provider's authorization endpoint.</li>
 *   <li>User grants consent → provider redirects back to
 *       {@code /login/oauth2/code/{registrationId}} with a {@code code}
 *       parameter.</li>
 *   <li>Spring Security exchanges the code for an access token via a
 *       back-channel call to the provider's token endpoint.</li>
 *   <li>Spring Security calls {@link #loadUser} on this service, which in turn
 *       calls the provider's UserInfo endpoint (or derives info from the
 *       ID token for OIDC providers like Google).</li>
 *   <li>We persist/update the user in the database and return the standard
 *       {@link OAuth2User} so Spring Security can complete the login.</li>
 * </ol>
 *
 * <h2>GitHub vs Google attribute differences</h2>
 * <ul>
 *   <li><strong>GitHub</strong>: user id → {@code "id"} (integer), name →
 *       {@code "name"}, email → {@code "email"}, avatar → {@code "avatar_url"}.
 *       Note: GitHub may return {@code null} for {@code email} if the user's
 *       email is not publicly visible on their profile.</li>
 *   <li><strong>Google</strong>: user id → {@code "sub"}, name → {@code "name"},
 *       email → {@code "email"}, avatar → {@code "picture"}.</li>
 * </ul>
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    /** Service that handles the upsert logic for {@code AppUser} entities. */
    private final AppUserService appUserService;

    public CustomOAuth2UserService(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    /**
     * Called by Spring Security after it has obtained an access token and
     * fetched the user's attributes from the provider's UserInfo endpoint.
     *
     * <p>The method delegates the actual HTTP call to the parent
     * {@link DefaultOAuth2UserService#loadUser} and then maps the provider
     * attributes to a local {@link AppUser} entity.</p>
     *
     * @param userRequest contains the OAuth2 access token and client registration
     *                    metadata (registration id, scopes, etc.)
     * @return the standard {@link OAuth2User} returned by the parent; Spring
     *         Security uses this to build the {@code Authentication} object
     * @throws OAuth2AuthenticationException if the UserInfo endpoint call fails
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // Let the default service fetch user attributes from the provider's
        // UserInfo endpoint and create an OAuth2User with those attributes.
        OAuth2User oauth2User = super.loadUser(userRequest);

        // The registrationId matches the key used in application.yml, e.g. "github" or "google"
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // Extract provider-specific attributes and map them to our neutral fields
        String providerId = extractProviderId(registrationId, oauth2User);
        String name       = oauth2User.getAttribute("name");
        String email      = oauth2User.getAttribute("email");
        String avatarUrl  = extractAvatarUrl(registrationId, oauth2User);

        // Persist or update the user in the database (upsert)
        appUserService.upsertUser(registrationId, providerId, name, email, avatarUrl);

        // Return the standard OAuth2User so Spring Security can store it in
        // the SecurityContext and build the Authentication principal.
        return oauth2User;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Extracts the provider's opaque user identifier.
     *
     * <ul>
     *   <li>GitHub: the {@code "id"} attribute is an {@code Integer} –
     *       convert to {@code String}.</li>
     *   <li>Google: the {@code "sub"} attribute is already a {@code String}.</li>
     * </ul>
     */
    private String extractProviderId(String registrationId, OAuth2User user) {
        if ("github".equals(registrationId)) {
            // GitHub returns the user id as an Integer in the attribute map
            Object id = user.getAttribute("id");
            return id != null ? String.valueOf(id) : null;
        }
        // Google (and OIDC-compliant providers) use the "sub" claim as the subject
        return user.getAttribute("sub");
    }

    /**
     * Extracts the avatar/profile picture URL, normalising the different
     * attribute names used by each provider.
     *
     * <ul>
     *   <li>GitHub: {@code "avatar_url"}</li>
     *   <li>Google: {@code "picture"}</li>
     * </ul>
     */
    private String extractAvatarUrl(String registrationId, OAuth2User user) {
        if ("github".equals(registrationId)) {
            return user.getAttribute("avatar_url");
        }
        // Google uses "picture" for the profile image URL
        return user.getAttribute("picture");
    }
}
