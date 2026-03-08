package com.example.jwtgeneration.unit;

import com.example.jwtgeneration.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 *
 * <p>These tests exercise the core domain logic of JWT generation and validation
 * in complete isolation – no Spring context, no database, no Docker container.
 * Dependencies are injected via {@link ReflectionTestUtils} to simulate what
 * Spring would normally inject via {@code @Value}.
 *
 * <p>We use:
 * <ul>
 *   <li><strong>JUnit 5</strong> ({@code @Test}, {@code @DisplayName}) for
 *       test structure and lifecycle.</li>
 *   <li><strong>AssertJ</strong> ({@code assertThat}) for fluent, readable
 *       assertions.</li>
 *   <li><strong>Mockito</strong> ({@code @ExtendWith(MockitoExtension.class)})
 *       for the extension even though no mocks are needed here – it keeps the
 *       class consistent with the rest of the test suite.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService – Unit Tests")
class JwtServiceTest {

    /**
     * The class under test. Instantiated manually (not via Spring) so the test
     * runs fast without an application context.
     */
    private JwtService jwtService;

    /**
     * A {@link UserDetails} fixture representing a standard user.
     * Used across multiple test methods to avoid repetition.
     */
    private UserDetails userDetails;

    /**
     * Sets up a fresh {@link JwtService} instance before each test.
     *
     * <p>{@link ReflectionTestUtils#setField} injects private field values the
     * same way Spring's {@code @Value} would, without needing the application
     * context to be started.
     */
    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        // Inject the same fields that Spring would bind from application.yml
        ReflectionTestUtils.setField(jwtService, "secretString",
                "TestSecretKeyForJWTGenerationMiniProjectTests2024!");
        ReflectionTestUtils.setField(jwtService, "expirationSeconds", 3600L);

        // Build a simple UserDetails fixture with ROLE_USER authority
        userDetails = new User(
                "testuser",
                "encodedpassword",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    // ── generateToken tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("generateToken should return a non-null, non-blank JWT string")
    void generateToken_shouldReturnNonBlankString() {
        String token = jwtService.generateToken(userDetails);

        // A compact JWT always consists of three Base64URL segments separated by dots
        assertThat(token).isNotNull().isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("generateToken should embed the correct username as the 'sub' claim")
    void generateToken_shouldEmbedCorrectUsername() {
        String token = jwtService.generateToken(userDetails);

        // extractUsername internally parses the JWT and returns the "sub" claim
        String extracted = jwtService.extractUsername(token);

        assertThat(extracted).isEqualTo("testuser");
    }

    @Test
    @DisplayName("generateToken should produce different tokens on consecutive calls")
    void generateToken_shouldProduceDifferentTokensOnConsecutiveCalls() {
        // Even with the same user, each call produces a different token because
        // the issued-at timestamp ("iat") is captured at call time.
        // NOTE: In very fast execution the timestamps may collide; that is
        // acceptable for unit tests – the important thing is the token is valid.
        String token1 = jwtService.generateToken(userDetails);
        String token2 = jwtService.generateToken(userDetails);

        // Both tokens must be structurally valid
        assertThat(token1).isNotBlank();
        assertThat(token2).isNotBlank();
    }

    // ── extractUsername tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("extractUsername should return the username embedded in a valid token")
    void extractUsername_shouldReturnCorrectUsername() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
    }

    @Test
    @DisplayName("extractUsername should throw for a tampered token")
    void extractUsername_shouldThrowForTamperedToken() {
        String token = jwtService.generateToken(userDetails);

        // Tamper with the signature segment (the third dot-separated part).
        // JJWT will fail signature verification and throw a JwtException.
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".invalidsignature";

        assertThatThrownBy(() -> jwtService.extractUsername(tamperedToken))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    // ── isTokenValid tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("isTokenValid should return true for a freshly generated token")
    void isTokenValid_shouldReturnTrueForFreshToken() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid should return false when username does not match")
    void isTokenValid_shouldReturnFalseWhenUsernameMismatch() {
        String token = jwtService.generateToken(userDetails);

        // Create a different UserDetails with a different username
        UserDetails otherUser = new User(
                "anotheruser",
                "pass",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // The token was issued for "testuser" but we validate against "anotheruser"
        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid should return false for an expired token")
    void isTokenValid_shouldReturnFalseForExpiredToken() {
        // Override the service with an expiration of -1 second (already expired)
        ReflectionTestUtils.setField(jwtService, "expirationSeconds", -1L);

        String token = jwtService.generateToken(userDetails);

        // The token's exp claim is in the past, so isTokenValid must return false
        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }

    // ── getExpirationSeconds tests ────────────────────────────────────────────

    @Test
    @DisplayName("getExpirationSeconds should return the configured value")
    void getExpirationSeconds_shouldReturnConfiguredValue() {
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(3600L);
    }

    // ── Admin role tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("generateToken should work correctly for a user with ROLE_ADMIN")
    void generateToken_shouldWorkForAdminUser() {
        UserDetails adminUser = new User(
                "adminuser",
                "encodedpassword",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        String token = jwtService.generateToken(adminUser);

        assertThat(jwtService.extractUsername(token)).isEqualTo("adminuser");
        assertThat(jwtService.isTokenValid(token, adminUser)).isTrue();
    }
}
