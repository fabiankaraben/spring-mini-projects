package com.example.jwtvalidation.dto;

/**
 * Data Transfer Object (DTO) returned by the {@code POST /api/auth/login} endpoint.
 *
 * <p>On successful authentication the server responds with this JSON object
 * containing the signed JWT and enough metadata for the client to use it:</p>
 *
 * <pre>{@code
 * {
 *   "token": "eyJhbGciOiJIUzI1NiJ9...",
 *   "tokenType": "Bearer",
 *   "username": "alice",
 *   "role": "ROLE_USER",
 *   "expiresInSeconds": 3600
 * }
 * }</pre>
 *
 * <h2>How to use the token</h2>
 * <p>Attach the token to subsequent requests in the HTTP
 * {@code Authorization} header:</p>
 * <pre>{@code
 * Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
 * }</pre>
 * <p>The {@code JwtAuthenticationFilter} (the central piece of this project)
 * will intercept the request, extract this header, validate the token, and
 * populate the {@code SecurityContext} so that the request is treated as
 * authenticated.</p>
 */
public class LoginResponse {

    /** The signed, compact JWT string. */
    private String token;

    /**
     * Always {@code "Bearer"} – the authentication scheme defined in
     * <a href="https://datatracker.ietf.org/doc/html/rfc6750">RFC 6750</a>.
     */
    private String tokenType = "Bearer";

    /** The username of the authenticated user (mirrors the JWT's {@code sub} claim). */
    private String username;

    /** The user's role (e.g. {@code "ROLE_USER"} or {@code "ROLE_ADMIN"}). */
    private String role;

    /**
     * How many seconds from now the token will expire.
     * Clients can use this to schedule a token refresh before the token becomes invalid.
     */
    private long expiresInSeconds;

    public LoginResponse(String token, String username, String role, long expiresInSeconds) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.expiresInSeconds = expiresInSeconds;
    }

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
