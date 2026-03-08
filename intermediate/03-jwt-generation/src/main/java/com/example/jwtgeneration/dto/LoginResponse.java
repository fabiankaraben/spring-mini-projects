package com.example.jwtgeneration.dto;

/**
 * Data Transfer Object (DTO) returned to the client after a successful login.
 *
 * <p>The response contains the signed JWT string and metadata about the token
 * so that clients do not need to decode the JWT themselves to obtain basic info.
 *
 * <p>Example JSON response:
 * <pre>{@code
 * {
 *   "token": "eyJhbGciOiJIUzI1NiJ9...",
 *   "tokenType": "Bearer",
 *   "username": "john",
 *   "role": "ROLE_USER",
 *   "expiresInSeconds": 3600
 * }
 * }</pre>
 */
public class LoginResponse {

    /**
     * The signed JWT string. Clients must include this in subsequent requests
     * using the {@code Authorization: Bearer <token>} header.
     */
    private String token;

    /**
     * The token type. Always {@code "Bearer"} for JWT-based authentication.
     * This field is included so clients can construct the header value directly:
     * {@code Authorization: <tokenType> <token>}.
     */
    private String tokenType = "Bearer";

    /**
     * The username extracted from the authenticated principal. Returned for
     * convenience so clients can display the logged-in user without decoding
     * the JWT.
     */
    private String username;

    /**
     * The role granted to this user (e.g. {@code "ROLE_USER"} or
     * {@code "ROLE_ADMIN"}). Returned so clients can make UI decisions without
     * decoding the token.
     */
    private String role;

    /**
     * How many seconds from now until this token expires. Mirrors the
     * {@code exp} claim in the JWT and lets clients schedule a re-login
     * proactively.
     */
    private long expiresInSeconds;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by Jackson for JSON serialisation. */
    public LoginResponse() {}

    public LoginResponse(String token, String username, String role, long expiresInSeconds) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.expiresInSeconds = expiresInSeconds;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
