package com.example.rolebasedaccess.dto;

/**
 * DTO returned to the client after a successful login.
 *
 * <p>Contains the signed JWT that the client must store and include in the
 * {@code Authorization: Bearer <token>} header of subsequent requests, along
 * with metadata about the authenticated user and token lifetime.</p>
 */
public class LoginResponse {

    /**
     * The signed JWT string (e.g. {@code "eyJhbGci..."}).
     * The client must include this in every authenticated request.
     */
    private final String token;

    /**
     * Always {@code "Bearer"} – indicates the type of token as defined in
     * RFC 6750. Clients should prepend this to the token in the
     * {@code Authorization} header: {@code Authorization: Bearer <token>}.
     */
    private final String tokenType = "Bearer";

    /** The authenticated user's username, included for convenience. */
    private final String username;

    /**
     * The role assigned to the authenticated user (e.g. {@code "ROLE_ADMIN"}).
     * Included so clients can adapt their UI without parsing the JWT themselves.
     */
    private final String role;

    /**
     * Number of seconds until the token expires.
     * Clients may use this to schedule token renewal before expiry.
     */
    private final long expiresInSeconds;

    public LoginResponse(String token, String username, String role, long expiresInSeconds) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.expiresInSeconds = expiresInSeconds;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
