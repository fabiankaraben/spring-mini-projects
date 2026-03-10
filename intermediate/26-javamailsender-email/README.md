# JavaMailSender Email

A Spring Boot mini-project demonstrating how to send **plain text** and **HTML emails** using Spring's `JavaMailSender` abstraction. HTML email bodies are rendered from a **Thymeleaf template**. A REST API exposes the email sending functionality.

The project uses **Mailpit** as the local SMTP server — a lightweight email sink that captures all messages without delivering them to real recipients. It provides a web UI to inspect received emails.

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (Maven Wrapper included) |
| Docker & Docker Compose | Required to run the full stack |

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/javamailsender/
│   │   ├── JavaMailSenderApplication.java      # Spring Boot entry point
│   │   ├── controller/
│   │   │   ├── EmailController.java             # REST endpoints
│   │   │   └── GlobalExceptionHandler.java      # Centralised error handling
│   │   ├── dto/
│   │   │   ├── PlainTextEmailRequest.java        # Request DTO for plain text email
│   │   │   ├── HtmlEmailRequest.java             # Request DTO for HTML email
│   │   │   └── EmailResponse.java               # Response DTO
│   │   └── service/
│   │       ├── EmailService.java                 # Service interface
│   │       └── EmailServiceImpl.java             # JavaMailSender + Thymeleaf implementation
│   └── resources/
│       ├── application.yml                       # Spring Boot configuration
│       └── templates/email/
│           └── html-email.html                   # Thymeleaf HTML email template
└── test/
    ├── java/com/example/javamailsender/
    │   ├── EmailControllerIntegrationTest.java   # Full integration tests (Testcontainers + Mailpit)
    │   ├── dto/
    │   │   └── EmailRequestValidationTest.java   # Unit tests for DTO validation
    │   └── service/
    │       └── EmailServiceImplTest.java         # Unit tests for service logic
    └── resources/
        ├── application-integration-test.yml      # Test profile configuration
        ├── docker-java.properties                # Testcontainers Docker API fix
        └── testcontainers.properties             # Testcontainers Docker API fix
```

---

## Key Concepts

- **`JavaMailSender`** – Spring's main abstraction for sending emails. Auto-configured by `spring-boot-starter-mail` when `spring.mail.*` properties are present.
- **`SimpleMailMessage`** – Lightweight value object for plain text emails (no MIME).
- **`MimeMessage` + `MimeMessageHelper`** – Used for HTML emails; `MimeMessageHelper` simplifies building multipart MIME messages.
- **Thymeleaf** – Server-side template engine renders the HTML email body from `templates/email/html-email.html`, keeping HTML layout separate from Java code.
- **Mailpit** – Local SMTP sink used in Docker Compose and Testcontainers integration tests. Accepts all email but never delivers it externally.

---

## Running with Docker Compose

The full stack (application + Mailpit) is defined in `docker-compose.yml`.

### Start

```bash
docker compose up --build
```

This starts:
- **Spring Boot app** on `http://localhost:8080`
- **Mailpit web UI** on `http://localhost:8025` — inspect received emails here
- **Mailpit SMTP** on `localhost:1025` (used internally by the app)

### Stop

```bash
docker compose down
```

### View received emails

Open the Mailpit web UI in your browser:

```
http://localhost:8025
```

---

## API Endpoints

### `POST /api/email/plain` — Send a plain text email

| Field | Type | Required | Description |
|---|---|---|---|
| `to` | string | ✅ | Recipient email address |
| `subject` | string | ✅ | Email subject line |
| `body` | string | ✅ | Plain text email body |

### `POST /api/email/html` — Send an HTML email

| Field | Type | Required | Description |
|---|---|---|---|
| `to` | string | ✅ | Recipient email address |
| `subject` | string | ✅ | Email subject line |
| `recipientName` | string | ✅ | Name used in the personalised greeting |
| `message` | string | ✅ | Main message body injected into the HTML template |

---

## curl Examples

All examples assume the application is running (via Docker Compose or locally).

### Send a plain text email

```bash
curl -X POST http://localhost:8080/api/email/plain \
  -H "Content-Type: application/json" \
  -d '{
    "to": "recipient@example.com",
    "subject": "Hello from JavaMailSender",
    "body": "This is a plain text email sent by Spring Boot."
  }'
```

**Expected response:**

```json
{
  "status": "sent",
  "to": "recipient@example.com",
  "message": "Email sent successfully."
}
```

---

### Send an HTML email

```bash
curl -X POST http://localhost:8080/api/email/html \
  -H "Content-Type: application/json" \
  -d '{
    "to": "recipient@example.com",
    "subject": "Welcome to Spring Mail!",
    "recipientName": "John",
    "message": "Welcome to our platform. Your account has been successfully created."
  }'
```

**Expected response:**

```json
{
  "status": "sent",
  "to": "recipient@example.com",
  "message": "HTML email sent successfully."
}
```

---

### Validation error example (missing field)

```bash
curl -X POST http://localhost:8080/api/email/plain \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "No recipient",
    "body": "This will fail validation."
  }'
```

**Expected response (400 Bad Request):**

```json
{
  "status": "failed",
  "message": "Validation error: Recipient email address must not be blank; ..."
}
```

---

### Validation error example (invalid email format)

```bash
curl -X POST http://localhost:8080/api/email/plain \
  -H "Content-Type: application/json" \
  -d '{
    "to": "not-a-valid-email",
    "subject": "Test",
    "body": "Body text."
  }'
```

**Expected response (400 Bad Request):**

```json
{
  "status": "failed",
  "message": "Validation error: Recipient email address must be a valid email address"
}
```

---

## Running Tests

> **Requirements:** Docker must be running before executing the integration tests, as Testcontainers starts a Mailpit container automatically.

### Run all tests

```bash
./mvnw clean test
```

### Test categories

| Test class | Type | Description |
|---|---|---|
| `EmailRequestValidationTest` | Unit | Bean Validation constraints on request DTOs |
| `EmailServiceImplTest` | Unit | Service logic with mocked `JavaMailSender` and `TemplateEngine` |
| `EmailControllerIntegrationTest` | Integration | Full stack with Mailpit container via Testcontainers |

### Integration test details

`EmailControllerIntegrationTest` uses **Testcontainers** to start a real **Mailpit** Docker container. The test:

1. Starts a Mailpit container (`axllent/mailpit:latest`) with SMTP port 1025 and HTTP port 8025.
2. Overrides `spring.mail.host` and `spring.mail.port` via `@DynamicPropertySource` to point at the container.
3. Sends HTTP requests through the full Spring MVC stack with `MockMvc`.
4. Verifies email delivery by querying Mailpit's REST API (`GET /api/v1/messages`).

---

## Configuration Reference

| Property | Default | Description |
|---|---|---|
| `spring.mail.host` | `localhost` | SMTP server hostname |
| `spring.mail.port` | `1025` | SMTP server port |
| `spring.mail.username` | _(empty)_ | SMTP username (not required for Mailpit) |
| `spring.mail.password` | _(empty)_ | SMTP password (not required for Mailpit) |
| `app.mail.from` | `noreply@example.com` | Sender address shown on all outgoing emails |

All properties can be overridden via environment variables (Docker Compose / Kubernetes style):

```bash
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=youruser@gmail.com
SPRING_MAIL_PASSWORD=yourpassword
APP_MAIL_FROM=youruser@gmail.com
```
