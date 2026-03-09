# 05 — OAuth2 Login Client

A Spring Boot backend that allows users to log in via **GitHub** or **Google** using the
**OAuth2 Authorization Code flow**. After a successful login the authenticated user's profile
is persisted to a PostgreSQL database and exposed through a small REST API.

---

## What this mini-project demonstrates

| Concept | Details |
|---|---|
| OAuth2 Authorization Code Grant | RFC 6749 §4.1 – browser-based login via GitHub or Google |
| Spring Security OAuth2 Client | `spring-boot-starter-oauth2-client`, `OAuth2LoginConfigurer` |
| Custom `OAuth2UserService` | Maps provider attributes to a local `AppUser` entity |
| JPA persistence | `AppUser` entity stored in PostgreSQL; upsert on every login |
| REST API | `/api/me`, `/api/users`, `/api/users/{id}`, `/api/me/attributes` |
| Unit tests | Plain JUnit 5 + Mockito – no Spring context, no Docker |
| Integration tests | Full Spring MVC + Security + real PostgreSQL via **Testcontainers** |

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use the included Maven Wrapper `./mvnw`) |
| Docker Desktop | 4.x+ (for running the app and integration tests) |
| GitHub OAuth App | Client ID + Secret (see setup below) |
| Google OAuth 2.0 App | Client ID + Secret (see setup below) |

---

## OAuth2 Provider Setup

### GitHub OAuth App

1. Go to **GitHub → Settings → Developer settings → OAuth Apps → New OAuth App**
   (or visit <https://github.com/settings/developers>).
2. Fill in:
   - **Application name**: `OAuth2 Login Client (local)`
   - **Homepage URL**: `http://localhost:8080`
   - **Authorization callback URL**: `http://localhost:8080/login/oauth2/code/github`
3. Click **Register application**.
4. Copy the **Client ID** and generate a **Client Secret**.

### Google OAuth 2.0 App

1. Go to **Google Cloud Console → APIs & Services → Credentials → Create Credentials →
   OAuth client ID** (or visit <https://console.developers.google.com/>).
2. Select **Web application** and fill in:
   - **Authorized redirect URIs**: `http://localhost:8080/login/oauth2/code/google`
3. Copy the **Client ID** and **Client Secret**.

---

## Running with Docker Compose (recommended)

The entire application stack (Spring Boot app + PostgreSQL) runs inside Docker Compose.
You supply the OAuth2 credentials through environment variables — **never commit real
credentials to Git**.

### 1. Export your credentials

```bash
export GITHUB_CLIENT_ID=your-github-client-id
export GITHUB_CLIENT_SECRET=your-github-client-secret
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
```

Alternatively create a `.env` file in this directory (it is git-ignored):

```
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

### 2. Build and start

```bash
docker compose up --build
```

Docker Compose will:
1. Pull `postgres:16-alpine` and start the database container.
2. Build the Spring Boot application image using the multi-stage `Dockerfile`.
3. Start the application container once the database healthcheck passes.

The application is ready when you see:

```
oauth2login-app  | Started OAuth2LoginClientApplication in X.XXX seconds
```

### 3. Stop the stack

```bash
docker compose down
```

To also remove the PostgreSQL data volume:

```bash
docker compose down -v
```

---

## Running locally (without Docker Compose)

If you have a local PostgreSQL instance on port 5432 and a database named `oauth2logindb`:

```bash
export GITHUB_CLIENT_ID=your-github-client-id
export GITHUB_CLIENT_SECRET=your-github-client-secret
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret

./mvnw spring-boot:run
```

---

## OAuth2 Login Flow

Because the OAuth2 Authorization Code flow requires a browser to follow redirects, the
login steps cannot be performed with `curl` alone. The typical interaction is:

1. **Start the stack** with `docker compose up --build`.
2. **Open a browser** and navigate to `http://localhost:8080/`.
3. Follow one of the login links:
   - **GitHub**: `http://localhost:8080/oauth2/authorization/github`
   - **Google**: `http://localhost:8080/oauth2/authorization/google`
4. Authorize the app on the provider's consent screen.
5. The provider redirects back to Spring Security's callback URL
   (`/login/oauth2/code/{registrationId}`).
6. Spring Security exchanges the code for an access token, calls the UserInfo endpoint,
   and redirects to `/api/me` where the authenticated profile is displayed.

---

## REST API Reference

All `/api/**` endpoints require an authenticated session. Use your browser (after
completing the OAuth2 login flow above) or a REST client like **Insomnia** or
**Postman** that can handle cookie-based sessions.

> `curl` examples below assume you have a valid session cookie stored in `cookies.txt`
> obtained after a browser login. Replace `<SESSION_COOKIE>` with the actual
> `JSESSIONID` value from your browser's DevTools.

### `GET /` — Welcome (public)

```bash
curl -s http://localhost:8080/
```

**Response**:
```json
{
  "message": "Welcome to the OAuth2 Login Client",
  "loginLinks": {
    "github": "/oauth2/authorization/github",
    "google": "/oauth2/authorization/google"
  },
  "apiDocs": {
    "currentUser": "/api/me",
    "allUsers": "/api/users"
  }
}
```

---

### `GET /api/me` — Current user profile (requires auth)

Returns the database-stored profile of the currently logged-in user.

```bash
curl -s -b "JSESSIONID=<SESSION_COOKIE>" http://localhost:8080/api/me
```

**Response** (example — GitHub user):
```json
{
  "id": 1,
  "provider": "github",
  "name": "The Octocat",
  "email": "octocat@github.com",
  "avatarUrl": "https://avatars.githubusercontent.com/u/583231",
  "createdAt": "2024-01-15T10:30:00Z",
  "lastLoginAt": "2024-01-15T12:45:00Z"
}
```

---

### `GET /api/users` — List all users (requires auth)

Returns all users that have ever logged in via OAuth2.

```bash
curl -s -b "JSESSIONID=<SESSION_COOKIE>" http://localhost:8080/api/users
```

**Response**:
```json
[
  {
    "id": 1,
    "provider": "github",
    "name": "The Octocat",
    "email": "octocat@github.com",
    "avatarUrl": "https://avatars.githubusercontent.com/u/583231",
    "createdAt": "2024-01-15T10:30:00Z",
    "lastLoginAt": "2024-01-15T12:45:00Z"
  },
  {
    "id": 2,
    "provider": "google",
    "name": "Jane Doe",
    "email": "jane.doe@gmail.com",
    "avatarUrl": "https://lh3.googleusercontent.com/photo/jane",
    "createdAt": "2024-01-15T11:00:00Z",
    "lastLoginAt": "2024-01-15T11:00:00Z"
  }
]
```

---

### `GET /api/users/{id}` — Single user by id (requires auth)

```bash
curl -s -b "JSESSIONID=<SESSION_COOKIE>" http://localhost:8080/api/users/1
```

**Response**: same shape as `/api/me`, or `404 Not Found` if the id does not exist.

---

### `GET /api/me/attributes` — Raw OAuth2 attributes (requires auth)

Returns the raw attribute map returned by the OAuth2 provider. Useful during
development to inspect exactly which fields the provider sends.

```bash
curl -s -b "JSESSIONID=<SESSION_COOKIE>" http://localhost:8080/api/me/attributes
```

**Response** (example — GitHub):
```json
{
  "id": 583231,
  "login": "octocat",
  "name": "The Octocat",
  "email": "octocat@github.com",
  "avatar_url": "https://avatars.githubusercontent.com/u/583231",
  "html_url": "https://github.com/octocat"
}
```

### `GET /logout` — Log out (requires auth)

Invalidates the session. Redirect your browser to:

```
http://localhost:8080/logout
```

(POST with CSRF token is required from a form; a browser GET will show Spring's
default logout page.)

---

## Running the Tests

### Unit tests only (no Docker required)

```bash
./mvnw test -Dtest="AppUserTest,AppUserServiceTest"
```

### All tests (unit + integration — Docker must be running)

The integration tests use **Testcontainers** to spin up a real PostgreSQL container
automatically. Docker Desktop must be running before executing these tests.

```bash
./mvnw clean test
```

Expected output:

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0  (AppUserTest)
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0  (AppUserServiceTest)
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0  (UserControllerIntegrationTest)
[INFO] BUILD SUCCESS
```

### Test categories

| Test class | Type | Requires Docker |
|---|---|---|
| `AppUserTest` | Unit — domain entity | No |
| `AppUserServiceTest` | Unit — service layer (Mockito) | No |
| `UserControllerIntegrationTest` | Integration — full stack + PostgreSQL | Yes |

---

## Project Structure

```
05-oauth2-login-client/
├── src/
│   ├── main/
│   │   ├── java/com/example/oauth2loginclient/
│   │   │   ├── OAuth2LoginClientApplication.java   # Spring Boot entry point
│   │   │   ├── controller/
│   │   │   │   ├── HomeController.java             # Public welcome endpoint
│   │   │   │   └── UserController.java             # /api/me, /api/users endpoints
│   │   │   ├── domain/
│   │   │   │   └── AppUser.java                    # JPA entity for persisted users
│   │   │   ├── dto/
│   │   │   │   └── UserProfileDto.java             # Read-only API response record
│   │   │   ├── repository/
│   │   │   │   └── AppUserRepository.java          # Spring Data JPA repository
│   │   │   ├── security/
│   │   │   │   ├── CustomOAuth2UserService.java    # Persists user after OAuth2 login
│   │   │   │   └── SecurityConfig.java             # HttpSecurity + oauth2Login()
│   │   │   └── service/
│   │   │       └── AppUserService.java             # Upsert + query logic
│   │   └── resources/
│   │       └── application.yml                     # App config + OAuth2 registration
│   └── test/
│       ├── java/com/example/oauth2loginclient/
│       │   ├── domain/
│       │   │   └── AppUserTest.java                # Unit tests for AppUser entity
│       │   ├── integration/
│       │   │   └── UserControllerIntegrationTest.java  # Testcontainers integration tests
│       │   └── service/
│       │       └── AppUserServiceTest.java         # Unit tests for AppUserService
│       └── resources/
│           ├── application-test.yml                # Test datasource + dummy OAuth2 creds
│           ├── docker-java.properties              # Docker API version fix for Testcontainers
│           └── testcontainers.properties           # Testcontainers Docker API version
├── .gitignore
├── Dockerfile                                      # Multi-stage build image
├── docker-compose.yml                              # App + PostgreSQL stack
├── mvnw / mvnw.cmd                                 # Maven Wrapper
├── pom.xml
└── README.md
```

---

## Key Concepts Explained

### OAuth2 Authorization Code Flow

```
Browser → GET /oauth2/authorization/github
        ← 302 Redirect to https://github.com/login/oauth/authorize?...
Browser → GET https://github.com/login/oauth/authorize (user consents)
        ← 302 Redirect to /login/oauth2/code/github?code=abc&state=xyz
Browser → GET /login/oauth2/code/github?code=abc&state=xyz
Spring Security (back-channel) → POST https://github.com/login/oauth/access_token
                                ← { access_token: "gho_..." }
Spring Security (back-channel) → GET https://api.github.com/user (UserInfo)
                                ← { id: 583231, login: "octocat", ... }
CustomOAuth2UserService.loadUser() → upsertUser() → AppUser saved in DB
        ← 302 Redirect to /api/me
Browser → GET /api/me
        ← 200 { id: 1, provider: "github", name: "The Octocat", ... }
```

### Upsert Strategy

On every successful OAuth2 login `AppUserService.upsertUser()` checks whether a row
already exists for the `(provider, providerId)` pair:

- **First login** → `INSERT` a new `AppUser` row.
- **Subsequent logins** → `UPDATE` the mutable fields (`name`, `email`, `avatarUrl`)
  and refresh `lastLoginAt`. This ensures the profile stays current if the user
  changes their name or avatar on the provider's platform.

### Provider Attribute Differences

| Attribute | GitHub | Google |
|---|---|---|
| User ID | `id` (Integer) | `sub` (String) |
| Display name | `name` | `name` |
| Email | `email` (may be null) | `email` |
| Avatar | `avatar_url` | `picture` |
