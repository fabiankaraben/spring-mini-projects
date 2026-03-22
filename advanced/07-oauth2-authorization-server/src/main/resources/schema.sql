-- =============================================================================
-- OAuth2 Authorization Server — Database Schema
--
-- This file contains the table definitions required by Spring Authorization Server
-- for JDBC-backed persistence of:
--   1. oauth2_registered_client      — registered OAuth2 client applications
--   2. oauth2_authorization          — issued OAuth2 authorizations (tokens)
--   3. oauth2_authorization_consent  — user consent records (which scopes the user
--                                      approved for which client)
--
-- Source: these DDL statements are adapted from the official Spring Authorization
-- Server schema scripts bundled inside the library JAR at:
--   org/springframework/security/oauth2/server/authorization/
--     oauth2-registered-client-schema.sql
--     oauth2-authorization-schema.sql
--     oauth2-authorization-consent-schema.sql
--
-- All tables use IF NOT EXISTS so this script is idempotent (safe to re-run on
-- every application startup without errors).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Table: oauth2_registered_client
--
-- Stores one row per registered OAuth2 client application.
-- Each client has a unique client_id (public) and a hashed client_secret.
--
-- Column explanations:
--   id                           — internal surrogate primary key (UUID string)
--   client_id                    — the public client identifier (e.g., "messaging-client")
--   client_id_issued_at          — when the client was registered (for bookkeeping)
--   client_secret                — the hashed client secret (e.g., {noop}secret or {bcrypt}...)
--   client_secret_expires_at     — optional expiry for the client secret (NULL = never expires)
--   client_name                  — human-readable display name
--   client_authentication_methods— comma-separated auth methods (client_secret_basic, etc.)
--   authorization_grant_types    — comma-separated grant types the client may use
--   redirect_uris                — comma-separated allowed redirect URIs (authorization_code flow)
--   post_logout_redirect_uris    — comma-separated post-logout redirect URIs (OIDC logout)
--   scopes                       — comma-separated scopes this client is allowed to request
--   client_settings              — JSON blob for client-specific settings (PKCE requirement, etc.)
--   token_settings               — JSON blob for token lifetime and other settings
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id                            VARCHAR(100)  NOT NULL,
    client_id                     VARCHAR(100)  NOT NULL,
    client_id_issued_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret                 VARCHAR(200)  DEFAULT NULL,
    client_secret_expires_at      TIMESTAMP     DEFAULT NULL,
    client_name                   VARCHAR(200)  NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL,
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- Table: oauth2_authorization
--
-- Stores one row per issued OAuth2 authorization (i.e., per token issuance).
-- A single authorization may contain an access token, a refresh token, and/or
-- an authorization code — all stored in this single row.
--
-- Column explanations:
--   id                               — unique authorization ID
--   registered_client_id             — FK to oauth2_registered_client.id
--   principal_name                   — the username or client ID that was authorized
--   authorization_grant_type         — the grant type used (e.g., authorization_code)
--   authorized_scopes                — scopes that were actually granted
--   attributes                       — JSON blob of authorization attributes
--   state                            — CSRF state parameter (authorization_code flow)
--   authorization_code_value         — the authorization code (hashed), if any
--   authorization_code_issued_at     — when the code was issued
--   authorization_code_expires_at    — when the code expires
--   authorization_code_metadata      — JSON metadata for the authorization code
--   access_token_value               — the access token (hashed)
--   access_token_issued_at           — when the access token was issued
--   access_token_expires_at          — when the access token expires
--   access_token_metadata            — JSON metadata for the access token
--   access_token_type                — token type (always "Bearer")
--   access_token_scopes              — scopes granted with this access token
--   oidc_id_token_value              — the OIDC ID token (hashed), if any
--   oidc_id_token_issued_at          — when the ID token was issued
--   oidc_id_token_expires_at         — when the ID token expires
--   oidc_id_token_metadata           — JSON metadata for the ID token
--   refresh_token_value              — the refresh token (hashed), if any
--   refresh_token_issued_at          — when the refresh token was issued
--   refresh_token_expires_at         — when the refresh token expires
--   refresh_token_metadata           — JSON metadata for the refresh token
--   user_code_value                  — device flow user code, if any
--   user_code_issued_at              — when the user code was issued
--   user_code_expires_at             — when the user code expires
--   user_code_metadata               — JSON metadata for the user code
--   device_code_value                — device flow device code, if any
--   device_code_issued_at            — when the device code was issued
--   device_code_expires_at           — when the device code expires
--   device_code_metadata             — JSON metadata for the device code
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                            VARCHAR(100)  NOT NULL,
    registered_client_id          VARCHAR(100)  NOT NULL,
    principal_name                VARCHAR(200)  NOT NULL,
    authorization_grant_type      VARCHAR(100)  NOT NULL,
    authorized_scopes             VARCHAR(1000) DEFAULT NULL,
    attributes                    TEXT          DEFAULT NULL,
    state                         VARCHAR(500)  DEFAULT NULL,
    authorization_code_value      TEXT          DEFAULT NULL,
    authorization_code_issued_at  TIMESTAMP     DEFAULT NULL,
    authorization_code_expires_at TIMESTAMP     DEFAULT NULL,
    authorization_code_metadata   TEXT          DEFAULT NULL,
    access_token_value            TEXT          DEFAULT NULL,
    access_token_issued_at        TIMESTAMP     DEFAULT NULL,
    access_token_expires_at       TIMESTAMP     DEFAULT NULL,
    access_token_metadata         TEXT          DEFAULT NULL,
    access_token_type             VARCHAR(100)  DEFAULT NULL,
    access_token_scopes           VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value           TEXT          DEFAULT NULL,
    oidc_id_token_issued_at       TIMESTAMP     DEFAULT NULL,
    oidc_id_token_expires_at      TIMESTAMP     DEFAULT NULL,
    oidc_id_token_metadata        TEXT          DEFAULT NULL,
    refresh_token_value           TEXT          DEFAULT NULL,
    refresh_token_issued_at       TIMESTAMP     DEFAULT NULL,
    refresh_token_expires_at      TIMESTAMP     DEFAULT NULL,
    refresh_token_metadata        TEXT          DEFAULT NULL,
    user_code_value               TEXT          DEFAULT NULL,
    user_code_issued_at           TIMESTAMP     DEFAULT NULL,
    user_code_expires_at          TIMESTAMP     DEFAULT NULL,
    user_code_metadata            TEXT          DEFAULT NULL,
    device_code_value             TEXT          DEFAULT NULL,
    device_code_issued_at         TIMESTAMP     DEFAULT NULL,
    device_code_expires_at        TIMESTAMP     DEFAULT NULL,
    device_code_metadata          TEXT          DEFAULT NULL,
    PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- Table: oauth2_authorization_consent
--
-- Stores one row per (client, principal) pair where the user has granted consent
-- to at least one scope. This table enables "remember my consent" so users don't
-- have to re-approve scopes on every authorization_code request.
--
-- Column explanations:
--   registered_client_id — FK to oauth2_registered_client.id
--   principal_name       — the username that granted consent
--   authorities          — comma-separated granted scopes/authorities
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
