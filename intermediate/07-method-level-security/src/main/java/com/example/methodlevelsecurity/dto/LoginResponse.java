package com.example.methodlevelsecurity.dto;

/**
 * DTO for the login response body.
 *
 * <p>Returned by {@code POST /api/auth/login} on successful authentication.
 * Contains the signed JWT that the client must include as a
 * {@code Authorization: Bearer <token>} header on all subsequent requests.</p>
 */
public class LoginResponse {

    /** The signed JWT string the client should store and send with future requests. */
    private final String token;

    /** The role of the authenticated user, included for client-side display purposes. */
    private final String role;

    public LoginResponse(String token, String role) {
        this.token = token;
        this.role = role;
    }

    public String getToken() { return token; }

    public String getRole() { return role; }
}
