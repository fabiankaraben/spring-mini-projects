package com.example.jwtvalidation.unit;

import com.example.jwtvalidation.service.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 *
 * <h2>Strategy</h2>
 * <p>Because {@link JwtService} has no Spring dependencies beyond
 * {@code @Value}-injected fields, we can test it as a plain Java object by
 * using Spring's {@link ReflectionTestUtils#setField} to inject the
 * configuration values directly – no {@code @SpringBootTest} or database
 * needed.</p>
 *
 * <p>This keeps the tests fast (milliseconds, not seconds) and fully isolated
 * from any infrastructure concern.</p>
 *
 * <h2>What is tested</h2>
 * <ul>
 *   <li>Token generation – produces a three-segment compact JWT.</li>
 *   <li>Claim extraction – {@code sub}, {@code role}, {@code exp} claims.</li>
 *   <li>Validation – valid token, expired token, wrong subject, tampered signature.</li>
 * </ul>
 */
@DisplayName("JwtService – Unit Tests")
class JwtServiceTest {

    /**
     * A secret long enough for HS256 (at least 32 bytes / 256 bits).
     * Fixed value so tests are deterministic.
     */
    private static final String TEST_SECRET =
            "TestSecretKeyForJWTValidationMiniProjectTesting!2024xx";

    /** Default expiration used by most tests (1 hour). */
    private static final long EXPIRATION_SECONDS = 3600L;

    /** The service under test – created as a plain object (no Spring context). */
    private JwtService jwtService;

    /** A generic test user with ROLE_USER authority. */
    private UserDetails userDetails;

    /**
     * Initialises a fresh {@link JwtService} and a test {@link UserDetails}
     * before every test method.
     */
    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Inject @Value fields directly using Spring's ReflectionTestUtils
        ReflectionTestUtils.setField(jwtService, "secretString", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationSeconds", EXPIRATION_SECONDS);

        // Build a UserDetails object that represents "testuser" with ROLE_USER
        userDetails = new User(
                "testuser",
                "encodedpassword",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    // ── Token generation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("generateToken should return a non-null, non-blank token string")
    void generateToken_shouldReturnNonBlankString() {
        String token = jwtService.generateToken(userDetails);

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("generateToken should return a JWT with exactly three dot-separated segments")
    void generateToken_shouldReturnWellStructuredJwt() {
        // A compact JWT always has exactly three Base64URL segments: header.payload.signature
        String token = jwtService.generateToken(userDetails);
        String[] parts = token.split("\\.");

        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isNotBlank(); // header
        assertThat(parts[1]).isNotBlank(); // payload
        assertThat(parts[2]).isNotBlank(); // signature
    }

    // ── Claim extraction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("extractUsername should return the username from the sub claim")
    void extractUsername_shouldReturnCorrectUsername() {
        String token = jwtService.generateToken(userDetails);

        String extractedUsername = jwtService.extractUsername(token);

        assertThat(extractedUsername).isEqualTo("testuser");
    }

    @Test
    @DisplayName("extractRole should return the role embedded in the token")
    void extractRole_shouldReturnRoleFromToken() {
        String token = jwtService.generateToken(userDetails);

        String role = jwtService.extractRole(token);

        assertThat(role).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("extractExpiration should return a future date")
    void extractExpiration_shouldReturnFutureDate() {
        String token = jwtService.generateToken(userDetails);

        Date expiration = jwtService.extractExpiration(token);

        // The expiration date must be after the current time
        assertThat(expiration).isAfter(new Date());
    }

    @Test
    @DisplayName("getExpirationSeconds should return the configured value")
    void getExpirationSeconds_shouldReturnConfiguredValue() {
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(EXPIRATION_SECONDS);
    }

    // ── Token validation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("isTokenValid should return true for a freshly generated token")
    void isTokenValid_shouldReturnTrue_forValidToken() {
        String token = jwtService.generateToken(userDetails);

        boolean valid = jwtService.isTokenValid(token, userDetails);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("isTokenValid should return false when the token subject does not match the UserDetails username")
    void isTokenValid_shouldReturnFalse_whenSubjectMismatch() {
        // Generate a token for "testuser"
        String token = jwtService.generateToken(userDetails);

        // Try to validate it against a different user
        UserDetails differentUser = new User(
                "otheruser",
                "encodedpassword",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        boolean valid = jwtService.isTokenValid(token, differentUser);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("isTokenValid should return false for an already-expired token")
    void isTokenValid_shouldReturnFalse_forExpiredToken() {
        // Create a JwtService configured with a very short expiration (-1 second = already expired)
        JwtService expiredJwtService = new JwtService();
        ReflectionTestUtils.setField(expiredJwtService, "secretString", TEST_SECRET);
        // Negative expiration means the token was expired before it was even issued
        ReflectionTestUtils.setField(expiredJwtService, "expirationSeconds", -1L);

        String expiredToken = expiredJwtService.generateToken(userDetails);

        // Validate with the normal service (uses the same secret, so signature is valid,
        // but the token is already past its expiry)
        boolean valid = jwtService.isTokenValid(expiredToken, userDetails);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("isTokenValid should return false for a token signed with a different secret")
    void isTokenValid_shouldReturnFalse_forTamperedSignature() {
        // Create a second JwtService with a DIFFERENT secret key
        JwtService differentKeyService = new JwtService();
        ReflectionTestUtils.setField(differentKeyService, "secretString",
                "ACompletelyDifferentSecretKeyForJWTTamperingTest!2024");
        ReflectionTestUtils.setField(differentKeyService, "expirationSeconds", EXPIRATION_SECONDS);

        // Generate a token with the different key
        String tokenWithWrongKey = differentKeyService.generateToken(userDetails);

        // The original jwtService uses TEST_SECRET – the signature will not match
        boolean valid = jwtService.isTokenValid(tokenWithWrongKey, userDetails);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("extractUsername should throw JwtException for a completely malformed token")
    void extractUsername_shouldThrowException_forMalformedToken() {
        // "not.a.valid.jwt" has too many segments and is not Base64URL-encoded
        assertThatThrownBy(() -> jwtService.extractUsername("not.a.valid.jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("isTokenValid should return false for a completely malformed token")
    void isTokenValid_shouldReturnFalse_forMalformedToken() {
        // isTokenValid must not propagate exceptions – it should just return false
        boolean valid = jwtService.isTokenValid("this.is.garbage", userDetails);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("generateToken should embed ROLE_ADMIN for an admin user")
    void generateToken_shouldEmbedAdminRole_forAdminUser() {
        UserDetails adminUser = new User(
                "adminuser",
                "encodedpassword",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        String token = jwtService.generateToken(adminUser);
        String role = jwtService.extractRole(token);

        assertThat(role).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("generateToken should produce different tokens for the same user on consecutive calls")
    void generateToken_shouldProduceDifferentTokens_onConsecutiveCalls() throws InterruptedException {
        // JWT iat (issued-at) has second-level precision, so we must wait at least
        // one full second to guarantee the two tokens have different iat values and
        // therefore produce different signed strings.
        String token1 = jwtService.generateToken(userDetails);
        Thread.sleep(1100); // wait >1 s to ensure the iat second-value differs
        String token2 = jwtService.generateToken(userDetails);

        assertThat(token1).isNotEqualTo(token2);
    }
}
