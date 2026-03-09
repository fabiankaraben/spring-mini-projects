package com.example.methodlevelsecurity.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

/**
 * Service responsible for creating, parsing, and validating JSON Web Tokens (JWTs).
 *
 * <h2>JWT structure reminder</h2>
 * <p>A JWT consists of three Base64URL-encoded parts separated by dots:</p>
 * <ol>
 *   <li><strong>Header</strong> – algorithm and token type ({@code HS256}, {@code JWT}).</li>
 *   <li><strong>Payload</strong> – claims: {@code sub} (username), {@code role},
 *       {@code iat} (issued-at), {@code exp} (expiry).</li>
 *   <li><strong>Signature</strong> – HMAC-SHA256 of header + payload using the secret key.</li>
 * </ol>
 *
 * <h2>Why embed the role in the token?</h2>
 * <p>Embedding the role as a custom claim avoids a database round-trip on every
 * request. The {@link com.example.methodlevelsecurity.security.JwtAuthenticationFilter}
 * reads the role directly from the token and builds the {@code Authentication} object,
 * which {@code @PreAuthorize} then evaluates against.</p>
 *
 * <h2>Configuration</h2>
 * <p>The secret and expiration are injected from {@code application.yml} via
 * {@code @Value}. Override them with environment variables in production.</p>
 */
@Service
public class JwtService {

    /**
     * HMAC-SHA256 secret key material. Must be at least 32 characters for HS256.
     * Injected from {@code app.jwt.secret} in {@code application.yml}.
     */
    @Value("${app.jwt.secret}")
    private String secretString;

    /**
     * Token validity in seconds. Injected from {@code app.jwt.expiration-seconds}.
     */
    @Value("${app.jwt.expiration-seconds}")
    private long expirationSeconds;

    // ── Token generation ──────────────────────────────────────────────────────

    /**
     * Generates a signed JWT for the given user.
     *
     * <p>The token payload contains:</p>
     * <ul>
     *   <li>{@code sub} – the username (standard JWT "subject" claim).</li>
     *   <li>{@code role} – the user's role authority string (custom claim).</li>
     *   <li>{@code iat} – issued-at timestamp.</li>
     *   <li>{@code exp} – expiry timestamp ({@code iat + expirationSeconds}).</li>
     * </ul>
     *
     * @param username  the subject to embed in the token
     * @param roleValue the role authority string to embed (e.g. {@code "ROLE_ADMIN"})
     * @return the compact, signed JWT string
     */
    public String generateToken(String username, String roleValue) {
        Instant now = Instant.now();
        return Jwts.builder()
                // "sub" claim – identifies the principal (the username)
                .subject(username)
                // Custom "role" claim – read back by the authentication filter
                .claim("role", roleValue)
                // Standard "iat" claim – when this token was issued
                .issuedAt(Date.from(now))
                // Standard "exp" claim – when this token expires
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                // Sign with HS256 using the configured secret key
                .signWith(getSigningKey())
                .compact();
    }

    // ── Token extraction ──────────────────────────────────────────────────────

    /**
     * Extracts the username ({@code sub} claim) from a JWT.
     *
     * @param token the compact JWT string
     * @return the username stored in the {@code sub} claim
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the custom {@code role} claim from a JWT.
     *
     * @param token the compact JWT string
     * @return the role string (e.g. {@code "ROLE_USER"}), or {@code null} if absent
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Generic claim extractor using a {@link Function} resolver.
     *
     * @param <T>           the return type of the claim
     * @param token         the compact JWT string
     * @param claimsResolver a function that maps {@link Claims} to the desired value
     * @return the extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // ── Token validation ──────────────────────────────────────────────────────

    /**
     * Validates that a token belongs to the given user and has not expired.
     *
     * @param token       the compact JWT string
     * @param userDetails the loaded user details to compare against
     * @return {@code true} if the token is valid for this user
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        // The subject must match the username AND the token must not be expired
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Checks whether the token's {@code exp} claim is in the past.
     */
    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Parses and verifies the JWT signature, then returns all claims.
     *
     * <p>Calling {@code parseSignedClaims} automatically verifies the HMAC-SHA256
     * signature. If the signature is invalid or the token is malformed, a
     * {@link io.jsonwebtoken.JwtException} is thrown.</p>
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Derives an HMAC-SHA256 {@link SecretKey} from the configured secret string.
     *
     * <p>JJWT's {@link Keys#hmacShaKeyFor} creates a key appropriate for the
     * length of the provided bytes. A 32-byte (256-bit) key is the minimum for
     * {@code HS256}; longer strings produce stronger keys.</p>
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
