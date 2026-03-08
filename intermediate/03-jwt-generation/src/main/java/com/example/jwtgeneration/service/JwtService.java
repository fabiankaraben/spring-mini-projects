package com.example.jwtgeneration.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Core service responsible for all JWT operations in this project.
 *
 * <h2>What is a JWT?</h2>
 * A JSON Web Token is a compact, URL-safe string with three Base64URL-encoded
 * parts separated by dots:
 * <pre>
 *   header.payload.signature
 * </pre>
 * <ul>
 *   <li><strong>Header</strong> – algorithm ({@code HS256}) and token type.</li>
 *   <li><strong>Payload</strong> – claims: {@code sub} (subject/username),
 *       {@code iat} (issued-at), {@code exp} (expiry), plus any custom
 *       claims (e.g. {@code role}).</li>
 *   <li><strong>Signature</strong> – HMAC-SHA256 of header+payload using the
 *       secret key. Verifying the signature proves the token was not tampered
 *       with.</li>
 * </ul>
 *
 * <h2>Algorithm choice</h2>
 * This project uses {@code HS256} (HMAC-SHA256): a symmetric algorithm where
 * the same secret key is used to both sign and verify the token. It is the
 * most common choice for single-service backends. For multi-service
 * architectures, an asymmetric algorithm like {@code RS256} is preferred so
 * that only the issuer needs the private key.
 *
 * <h2>Key derivation</h2>
 * The secret is read from {@code app.jwt.secret} in {@code application.yml}.
 * JJWT's {@code Keys.hmacShaKeyFor()} derives a {@link SecretKey} from the
 * UTF-8 bytes of that string. The secret must be at least 32 bytes (256 bits)
 * to satisfy the HS256 minimum key length.
 */
@Service
public class JwtService {

    /**
     * The secret used to sign (and later verify) tokens.
     * Injected from {@code app.jwt.secret} in {@code application.yml}.
     *
     * <p>In production, store this value in an environment variable or a
     * secrets manager (e.g. AWS Secrets Manager, HashiCorp Vault) – never
     * hard-code it in version control.
     */
    @Value("${app.jwt.secret}")
    private String secretString;

    /**
     * Token validity duration in seconds.
     * Injected from {@code app.jwt.expiration-seconds} in {@code application.yml}.
     *
     * <p>Default is 3600 seconds (1 hour). Short-lived tokens reduce the
     * window of opportunity if a token is stolen.
     */
    @Value("${app.jwt.expiration-seconds}")
    private long expirationSeconds;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a signed JWT for the given {@link UserDetails}.
     *
     * <p>The generated token includes:
     * <ul>
     *   <li>{@code sub} – the username (from {@code userDetails.getUsername()})</li>
     *   <li>{@code role} – the first granted authority (e.g. {@code "ROLE_USER"})</li>
     *   <li>{@code iat} – timestamp when the token was issued</li>
     *   <li>{@code exp} – timestamp when the token expires</li>
     * </ul>
     *
     * @param userDetails the authenticated principal returned by
     *                    {@link org.springframework.security.core.userdetails.UserDetailsService}
     * @return a compact, signed JWT string (e.g. {@code "eyJhbGci..."})
     */
    public String generateToken(UserDetails userDetails) {
        // Build extra claims – here we embed the user's role so consumers of the
        // token can make authorisation decisions without a database round-trip.
        Map<String, Object> extraClaims = new HashMap<>();
        if (!userDetails.getAuthorities().isEmpty()) {
            extraClaims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        }
        return buildToken(extraClaims, userDetails);
    }

    /**
     * Extracts the {@code sub} (subject) claim from a JWT, which holds the
     * username of the authenticated user.
     *
     * @param token a compact JWT string
     * @return the username embedded in the token's subject claim
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Returns the configured token expiry duration in seconds.
     * Used by the controller to include {@code expiresInSeconds} in the response.
     *
     * @return number of seconds a freshly issued token is valid for
     */
    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /**
     * Checks whether a JWT is currently valid for the given user.
     *
     * <p>A token is considered valid if:
     * <ol>
     *   <li>The {@code sub} claim matches the username in {@code userDetails}.</li>
     *   <li>The {@code exp} claim is in the future (i.e. not expired).</li>
     * </ol>
     *
     * @param token       the JWT to validate
     * @param userDetails the principal to compare against the token's subject
     * @return {@code true} if the token is valid, {@code false} otherwise
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (ExpiredJwtException ex) {
            // JJWT throws ExpiredJwtException inside parseSignedClaims() when the
            // token's "exp" claim is in the past. We catch it here and return false
            // so callers get a clean boolean instead of an unchecked exception.
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds and signs the JWT using JJWT's fluent builder API.
     *
     * @param extraClaims additional claims to embed in the payload
     * @param userDetails the authenticated principal
     * @return a signed, compact JWT string
     */
    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long nowMillis = System.currentTimeMillis();
        Date issuedAt = new Date(nowMillis);
        Date expiration = new Date(nowMillis + expirationSeconds * 1000L);

        return Jwts.builder()
                // Embed any extra claims first (so standard claims below override them if
                // there is a name collision – e.g. nobody can inject a "sub" via extraClaims)
                .claims(extraClaims)
                // "sub" claim – who the token was issued for
                .subject(userDetails.getUsername())
                // "iat" claim – when the token was issued
                .issuedAt(issuedAt)
                // "exp" claim – when the token expires
                .expiration(expiration)
                // Sign with HMAC-SHA256 using our derived secret key
                .signWith(getSigningKey())
                // Serialise header.payload.signature → compact string
                .compact();
    }

    /**
     * Generic claim extractor. Accepts a resolver function so callers can
     * extract any claim without duplicating the parse boilerplate.
     *
     * <p>Example usage:
     * <pre>{@code
     * Date expiry = extractClaim(token, Claims::getExpiration);
     * }</pre>
     *
     * @param token          a compact JWT string
     * @param claimsResolver function that maps {@link Claims} to the desired value
     * @param <T>            type of the claim value
     * @return the resolved claim value
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses the JWT and returns all claims from its payload.
     *
     * <p>JJWT automatically verifies the signature during parsing. If the
     * signature is invalid or the token is malformed, a
     * {@link io.jsonwebtoken.JwtException} is thrown.
     *
     * @param token a compact JWT string
     * @return all claims embedded in the token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                // Provide the same signing key that was used to create the token
                .verifyWith(getSigningKey())
                .build()
                // parseSignedClaims verifies the signature AND parses the payload
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Checks whether the {@code exp} claim is before the current time.
     *
     * @param token a compact JWT string
     * @return {@code true} if the token has expired
     */
    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Derives a {@link SecretKey} from the configured secret string.
     *
     * <p>JJWT's {@code Keys.hmacShaKeyFor()} expects a byte array of at least
     * 256 bits (32 bytes) for HS256. The key is derived fresh on each call;
     * for high-throughput scenarios you may want to cache it as a field.
     *
     * @return an HMAC-SHA256-compatible {@link SecretKey}
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
