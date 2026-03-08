# Form Login Security

A Spring Boot mini-project demonstrating a complete **browser-based form login and logout flow** using Spring Security. Users are persisted in PostgreSQL and authenticated via an HTML login form backed by BCrypt-hashed passwords and session management.

---

## What This Project Covers

| Concept | Description |
|---|---|
| **Form Login** | Custom HTML login page; Spring Security processes `POST /login` |
| **Session Management** | Stateful HTTP session created on successful login |
| **CSRF Protection** | Enabled by default; Thymeleaf injects the `_csrf` token into every form |
| **BCrypt Passwords** | Passwords are stored as BCrypt hashes, never as plain text |
| **Role-Based Access** | `ROLE_USER` accesses `/dashboard` and `/profile`; `ROLE_ADMIN` also accesses `/admin` |
| **Secure Logout** | `POST /logout` invalidates the session and clears `JSESSIONID` cookie |
| **Thymeleaf Templates** | Server-rendered HTML; `sec:authorize` hides elements based on roles |
| **Spring Data JPA** | Users persisted in PostgreSQL via Hibernate |

---

## Requirements

- **Java 21+**
- **Docker** and **Docker Compose** (for running the full stack)
- **Maven** (or use the included Maven Wrapper `./mvnw`)

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/formlogin/
│   │   ├── FormLoginApplication.java       # Main entry point
│   │   ├── config/
│   │   │   ├── SecurityConfig.java         # Form login, logout, CSRF, role rules
│   │   │   └── DataInitializer.java        # Seeds demo users on startup
│   │   ├── controller/
│   │   │   └── PageController.java         # Maps URLs to Thymeleaf templates
│   │   ├── entity/
│   │   │   └── User.java                   # JPA entity (users table)
│   │   ├── repository/
│   │   │   └── UserRepository.java         # Spring Data JPA repository
│   │   └── security/
│   │       └── UserDetailsServiceImpl.java # Loads user from DB for Spring Security
│   └── resources/
│       ├── application.properties
│       └── templates/
│           ├── login.html                  # Custom login form
│           ├── dashboard.html              # Post-login landing page
│           ├── admin.html                  # Admin-only page
│           └── profile.html               # User profile page
└── test/
    └── java/com/example/formlogin/
        ├── security/
        │   └── UserDetailsServiceImplTest.java      # Unit tests (Mockito)
        └── controller/
            └── PageControllerIntegrationTest.java   # Integration tests (Testcontainers)
```

---

## Demo Users

The application seeds two users on first startup (see `DataInitializer`):

| Username | Password  | Role  | Can access              |
|----------|-----------|-------|-------------------------|
| `user`   | `password`| USER  | `/dashboard`, `/profile` |
| `admin`  | `admin123`| ADMIN | `/dashboard`, `/profile`, `/admin` |

---

## Running with Docker Compose

The entire stack (PostgreSQL + Spring Boot app) runs via Docker Compose:

```bash
# Build the image and start all services
docker compose up --build

# Run in the background (detached mode)
docker compose up --build -d

# View logs
docker compose logs -f

# Stop and remove containers (data volume is preserved)
docker compose down

# Stop and remove containers AND the database volume
docker compose down -v
```

Once running, open your browser at **http://localhost:8080/dashboard** — you will be redirected to the login page automatically.

---

## How to Use (Browser Flow)

### 1. Open the login page

```
http://localhost:8080/login
```

### 2. Sign in with a demo user

Enter `user` / `password` and click **Sign in**.  
You are redirected to `/dashboard`.

### 3. Visit role-restricted pages

- `/profile` — available to all authenticated users  
- `/admin` — **admin only**; visiting as `user` returns HTTP 403

### 4. Sign out

Click the **Logout** button in the navigation bar.  
The session is invalidated and you are redirected to `/login?logout`.

---

## How to Use (curl Examples)

> **Note:** Form login is session-based. curl must carry the session cookie between requests. The examples below use a cookie jar file (`cookies.txt`) for this purpose.

### Check the login page is publicly accessible

```bash
curl -v http://localhost:8080/login
# Expected: HTTP 200 with the HTML login form
```

### Verify unauthenticated access to a protected page is redirected

```bash
curl -v http://localhost:8080/dashboard
# Expected: HTTP 302 → Location: http://localhost:8080/login
```

### Log in as the regular user

```bash
curl -v -c cookies.txt -b cookies.txt \
  -X POST http://localhost:8080/login \
  -d "username=user&password=password&_csrf=CSRF_TOKEN"
# Expected: HTTP 302 → Location: http://localhost:8080/dashboard
```

> **CSRF note:** To obtain a valid CSRF token for curl, first `GET /login`, extract the `_csrf` hidden input value from the HTML, then include it in the POST. Spring Security rejects form POSTs without a valid token.
>
> Alternatively, for API-level testing (e.g. Postman, integration tests), the `spring-security-test` library's `csrf()` post-processor handles this automatically.

### Access the dashboard with the active session

```bash
curl -v -c cookies.txt -b cookies.txt http://localhost:8080/dashboard
# Expected: HTTP 200 with dashboard HTML
```

### Try to access the admin page as a regular user

```bash
curl -v -c cookies.txt -b cookies.txt http://localhost:8080/admin
# Expected: HTTP 403 Forbidden
```

### Log in as admin and access the admin page

```bash
# Login as admin
curl -v -c admin_cookies.txt -b admin_cookies.txt \
  -X POST http://localhost:8080/login \
  -d "username=admin&password=admin123&_csrf=CSRF_TOKEN"

# Access admin panel
curl -v -c admin_cookies.txt -b admin_cookies.txt http://localhost:8080/admin
# Expected: HTTP 200 with admin panel HTML
```

### Log out

```bash
curl -v -c cookies.txt -b cookies.txt \
  -X POST http://localhost:8080/logout \
  -d "_csrf=CSRF_TOKEN"
# Expected: HTTP 302 → Location: http://localhost:8080/login?logout
```

---

## Running Tests

Tests require **Docker** to be running (Testcontainers starts a real PostgreSQL container automatically).

```bash
# Run all tests (unit + integration)
./mvnw clean test
```

### Test categories

| Test class | Type | What it tests |
|---|---|---|
| `UserDetailsServiceImplTest` | Unit (Mockito, no Spring context) | `loadUserByUsername` success and failure paths |
| `PageControllerIntegrationTest` | Integration (Testcontainers + full Spring context) | Login form, logout, redirect rules, role-based access |

### Individual test class

```bash
# Run only unit tests
./mvnw test -Dtest=UserDetailsServiceImplTest

# Run only integration tests
./mvnw test -Dtest=PageControllerIntegrationTest
```

---

## Key Spring Security Concepts Explained

### Why CSRF is enabled here (but disabled in REST APIs)

Form-based applications are vulnerable to CSRF attacks because browsers automatically send session cookies with every request. CSRF protection is the default in Spring Security and Thymeleaf injects the `_csrf` hidden field into every form via `th:action="@{/login}"`.

REST APIs typically disable CSRF because they use stateless tokens (e.g. JWT) instead of session cookies.

### Session lifecycle

```
Login POST → session created → JSESSIONID cookie set
Requests   → session validated → principal available
Logout POST → session invalidated → JSESSIONID cookie deleted
```

### Why logout uses POST, not GET

A GET link to `/logout` could be triggered by a third-party page (CSRF via `<img src="/logout">`). Using POST with a CSRF token ensures only intentional logouts happen.
