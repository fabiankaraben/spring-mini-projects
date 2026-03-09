package com.example.rolebasedaccess.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
 * Service responsible for all JWT operations: generation, parsing, and validation.
 *
 * <h2>JWT anatomy</h2>
 * <p>A JSON Web Token is a compact, URL-safe string composed of three
 * Base64URL-encoded parts separated by dots:</p>
 * <pre>
 *   header.payload.signature
 * </pre>
 * <ul>
 *   <li><strong>Header</strong> – algorithm ({@code HS256}) and token type.</li>
 *   <li><strong>Payload</strong> – claims: {@code sub} (username),
 *       {@code iat} (issued-at), {@code exp} (expiry), plus custom
 *       claims such as {@code role}.</li>
 *   <li><strong>Signature</strong> – HMAC-SHA256 of header+payload using the
 *       secret key. Verifying it proves the token has not been tampered with.</li>
 * </ul>
 *
 * <h2>Role claim</h2>
 * <p>The {@code role} custom claim embedded in the JWT carries the user's role
 * (e.g. {@code "ROLE_ADMIN"}). This claim is used by the
 * {@code JwtAuthenticationFilter} to reconstruct the {@code Authentication}
 * object without a database round-trip, enabling stateless role-based access
 * control via {@code @PreAuthorize}.</p>
 */
@Service
public class JwtService {

    /**
     * The secret used to sign (and later verify) tokens.
     * Injected from {@code app.jwt.secret} in {@code application.yml}.
     *
     * <p>In production, store this in an environment variable or secrets
     * manager (e.g. AWS Secrets Manager, HashiCorp Vault) – never hard-code
     * it in version control.</p>
     */
    @Value("${app.jwt.secret}")
    private String secretString;

    /**
     * Token validity duration in seconds.
     * Injected from {@code app.jwt.expiration-seconds} in {@code application.yml}.
     */
    @Value("${app.jwt.expiration-seconds}")
    private long expirationSeconds;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a signed JWT for the given {@link UserDetails}.
     *
     * <p>The generated token includes:</p>
     * <ul>
     *   <li>{@code sub} – the username</li>
     *   <li>{@code role} – the first granted authority (e.g. {@code "ROLE_ADMIN"})</li>
     *   <li>{@code iat} – issued-at timestamp</li>
     *   <li>{@code exp} – expiry timestamp</li>
     * </ul>
     *
     * <p>Embedding the role in the token allows the JWT filter to reconstruct the
     * full {@code Authentication} object on each request without hitting the database,
     * keeping the API stateless.</p>
     *
     * @param userDetails the authenticated principal
     * @return a compact, signed JWT string (e.g. {@code "eyJhbGci..."})
     */
    public String generateToken(UserDetails userDetails) {
        // Build extra claims – embed the user's role so each request carries
        // authorisation info without needing a database round-trip.
        Map<String, Object> extraClaims = new HashMap<>();
        if (!userDetails.getAuthorities().isEmpty()) {
            extraClaims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        }
        return buildToken(extraClaims, userDetails);
    }

    /**
     * Extracts the {@code sub} (subject) claim from a JWT, which holds
     * the username of the authenticated user.
     *
     * @param token a compact JWT string
     * @return the username embedded in the token's subject claim
     * @throws JwtException if the token is malformed or the signature is invalid
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the {@code role} custom claim from the token payload.
     *
     * <p>This claim holds a string like {@code "ROLE_USER"} or
     * {@code "ROLE_ADMIN"} and is used by {@code JwtAuthenticationFilter} to
     * restore the correct authority in the {@code SecurityContext}.</p>
     *
     * @param token a compact JWT string
     * @return the role string, or {@code null} if the claim is absent
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Extracts the {@code exp} (expiration) claim from the token.
     *
     * @param token a compact JWT string
     * @return the {@link Date} at which the token expires
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Returns the configured token expiry duration in seconds.
     *
     * @return number of seconds a freshly issued token is valid for
     */
    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /**
     * Validates a JWT against the given {@link UserDetails}.
     *
     * <p>A token is considered valid if all of the following hold:</p>
     * <ol>
     *   <li>The HMAC-SHA256 signature verifies correctly with the configured secret.</li>
     *   <li>The {@code sub} claim matches the username in {@code userDetails}.</li>
     *   <li>The {@code exp} claim is in the future (token has not expired).</li>
     * </ol>
     *
     * @param token       the JWT to validate
     * @param userDetails the principal to compare against the token's subject
     * @return {@code true} if the token is valid; {@code false} otherwise
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            // extractUsername internally calls Jwts.parser().verifyWith(key)…
            // which validates the signature as a side-effect of parsing.
            final String username = extractUsername(token);

            // The subject must match the username of the user making the request.
            boolean subjectMatches = username.equals(userDetails.getUsername());

            // The token must not have passed its expiration date.
            boolean notExpired = !isTokenExpired(token);

            return subjectMatches && notExpired;

        } catch (ExpiredJwtException ex) {
            // JJWT throws ExpiredJwtException when the "exp" claim is in the past.
            return false;
        } catch (JwtException ex) {
            // Catches all other JWT failures (SignatureException, MalformedJwtException, etc.)
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
                // Embed extra claims first (so standard claims below override on collision)
                .claims(extraClaims)
                // "sub" claim – who the token was issued for
                .subject(userDetails.getUsername())
                // "iat" claim – when the token was issued
                .issuedAt(issuedAt)
                // "exp" claim – when the token expires
                .expiration(expiration)
                // Sign with HMAC-SHA256 using the derived secret key
                .signWith(getSigningKey())
                // Serialise to compact header.payload.signature string
                .compact();
    }

    /**
     * Generic claim extractor. Accepts a resolver function so callers can
     * extract any claim without duplicating the parse boilerplate.
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
     * <p>JJWT automatically verifies the HMAC-SHA256 signature during parsing.
     * If the signature is invalid or the token is malformed, a
     * {@link JwtException} is thrown.</p>
     *
     * @param token a compact JWT string
     * @return all claims embedded in the token
     * @throws JwtException if parsing or signature verification fails
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                // Provide the signing key – JJWT re-verifies the signature with it.
                .verifyWith(getSigningKey())
                .build()
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
     * 256 bits (32 bytes) for HS256.</p>
     *
     * @return an HMAC-SHA256-compatible {@link SecretKey}
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
