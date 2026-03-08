package com.example.jwtvalidation.service;

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
 * Core service responsible for all JWT operations: generation, parsing, and
 * <strong>validation</strong> (the focus of this project).
 *
 * <h2>JWT anatomy recap</h2>
 * <p>A JSON Web Token is a compact, URL-safe string composed of three
 * Base64URL-encoded parts separated by dots:</p>
 * <pre>
 *   header.payload.signature
 * </pre>
 * <ul>
 *   <li><strong>Header</strong> – algorithm ({@code HS256}) and token type.</li>
 *   <li><strong>Payload</strong> – claims: {@code sub} (subject/username),
 *       {@code iat} (issued-at), {@code exp} (expiry), plus any custom
 *       claims (e.g. {@code role}).</li>
 *   <li><strong>Signature</strong> – HMAC-SHA256 of the Base64URL(header) +
 *       "." + Base64URL(payload) using the secret key. Verifying the signature
 *       proves the token was not tampered with since it was issued.</li>
 * </ul>
 *
 * <h2>Validation steps (what this project focuses on)</h2>
 * <p>When a request arrives, the {@code JwtAuthenticationFilter} calls this
 * service to perform the following checks:</p>
 * <ol>
 *   <li><strong>Signature verification</strong> – JJWT re-computes the HMAC
 *       over header+payload using the same secret and compares it to the
 *       signature in the token. Any tampering of header or payload causes a
 *       mismatch and an exception.</li>
 *   <li><strong>Expiry check</strong> – the {@code exp} claim must be in the
 *       future. An expired token is rejected even if the signature is valid.</li>
 *   <li><strong>Subject match</strong> – the {@code sub} claim must match the
 *       username loaded from the database, ensuring the token was issued for
 *       the same user that is now making the request.</li>
 * </ol>
 *
 * <h2>Algorithm choice</h2>
 * <p>This project uses {@code HS256} (HMAC-SHA256): a symmetric algorithm where
 * the same secret key is used to both sign and verify the token. It is the
 * most common choice for single-service backends. For multi-service
 * architectures, an asymmetric algorithm like {@code RS256} is preferred so
 * that only the issuer needs the private key while verifiers only need the
 * public key.</p>
 *
 * <h2>Key derivation</h2>
 * <p>The secret is read from {@code app.jwt.secret} in {@code application.yml}.
 * JJWT's {@code Keys.hmacShaKeyFor()} derives a {@link SecretKey} from the
 * UTF-8 bytes of that string. The secret must be at least 32 bytes (256 bits)
 * to satisfy the HS256 minimum key length.</p>
 */
@Service
public class JwtService {

    /**
     * The secret used to sign (and later verify) tokens.
     * Injected from {@code app.jwt.secret} in {@code application.yml}.
     *
     * <p>In production, store this value in an environment variable or a
     * secrets manager (e.g. AWS Secrets Manager, HashiCorp Vault) – never
     * hard-code it in version control.</p>
     */
    @Value("${app.jwt.secret}")
    private String secretString;

    /**
     * Token validity duration in seconds.
     * Injected from {@code app.jwt.expiration-seconds} in {@code application.yml}.
     *
     * <p>Default is 3600 seconds (1 hour). Short-lived tokens reduce the
     * window of opportunity if a token is stolen.</p>
     */
    @Value("${app.jwt.expiration-seconds}")
    private long expirationSeconds;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a signed JWT for the given {@link UserDetails}.
     *
     * <p>The generated token includes:</p>
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
     * @throws JwtException if the token is malformed or the signature is invalid
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the {@code role} custom claim from the token payload.
     *
     * <p>This claim was embedded during token generation via
     * {@link #generateToken(UserDetails)}. It holds a string like
     * {@code "ROLE_USER"} or {@code "ROLE_ADMIN"} and is used by the
     * {@code ProtectedController} to display the caller's role.</p>
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
     * Used by the controller to include {@code expiresInSeconds} in the response.
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
     *   <li>The signature verifies correctly with the configured secret
     *       (checked implicitly by JJWT inside {@link #extractUsername}).</li>
     *   <li>The {@code sub} claim matches the username in {@code userDetails}.</li>
     *   <li>The {@code exp} claim is in the future (token is not expired).</li>
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
            // If the signature is invalid, a JwtException is thrown here.
            final String username = extractUsername(token);

            // The subject must match the username of the user making the request.
            boolean subjectMatches = username.equals(userDetails.getUsername());

            // The token must not have passed its expiration date.
            boolean notExpired = !isTokenExpired(token);

            return subjectMatches && notExpired;

        } catch (ExpiredJwtException ex) {
            // JJWT throws ExpiredJwtException inside parseSignedClaims() when the
            // token's "exp" claim is in the past. We catch it here and return false
            // so callers receive a clean boolean instead of an unchecked exception.
            return false;
        } catch (JwtException ex) {
            // Catches all other JWT failures:
            //   - SignatureException   – someone tampered with the token body
            //   - MalformedJwtException – not a valid JWT format (e.g. missing dot)
            //   - UnsupportedJwtException – unexpected JWT type
            // Returning false lets the filter respond with 401 without leaking details.
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
     * <p>Example usage:</p>
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
     * {@link io.jsonwebtoken.JwtException} is thrown.</p>
     *
     * <p><strong>This is the core validation step</strong>: by calling
     * {@code verifyWith(getSigningKey())} JJWT will:</p>
     * <ol>
     *   <li>Re-compute HMAC-SHA256 over the received header+payload using
     *       the same secret key.</li>
     *   <li>Compare the result to the signature segment in the token.</li>
     *   <li>Throw {@link io.jsonwebtoken.security.SignatureException} if they
     *       do not match, rejecting any tampered token.</li>
     * </ol>
     *
     * @param token a compact JWT string
     * @return all claims embedded in the token
     * @throws io.jsonwebtoken.JwtException if parsing or verification fails
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                // Provide the same signing key that was used to create the token.
                // JJWT uses this key to re-verify the signature.
                .verifyWith(getSigningKey())
                .build()
                // parseSignedClaims performs:
                //   1. Base64URL decode of header + payload
                //   2. Signature verification (throws on failure)
                //   3. Expiry check (throws ExpiredJwtException if exp is past)
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
     * for high-throughput scenarios you may want to cache it as a field.</p>
     *
     * @return an HMAC-SHA256-compatible {@link SecretKey}
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
