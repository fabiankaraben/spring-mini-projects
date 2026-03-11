# Twilio SMS Sender

A Spring Boot backend that sends SMS text messages by integrating the **Twilio REST API**. Every send attempt is persisted to a local PostgreSQL database for auditing and history tracking.

## What This Project Demonstrates

- **Twilio Java SDK** integration with Spring Boot
- **Twilio SDK initialization** via `@PostConstruct` and `@Configuration`
- **REST API** for sending SMS and querying message history
- **Spring Data JPA** + **PostgreSQL** for local SMS audit records
- **Bean Validation** (`@Valid`, `@Pattern`) for phone number and message validation
- **RFC 7807 Problem Details** for structured error responses
- **Unit testing** with JUnit 5 + Mockito (spy pattern for SDK isolation)
- **Integration testing** with Testcontainers (real PostgreSQL container)
- **Docker Compose** for running the full stack in containers

---

## Requirements

| Requirement     | Version        |
|-----------------|----------------|
| Java            | 21 or higher   |
| Maven           | (via wrapper)  |
| Docker          | 24+            |
| Docker Compose  | v2             |
| Twilio Account  | Free trial OK  |

### Twilio Setup

You need a free Twilio account to send real SMS messages:

1. Sign up at [https://www.twilio.com/try-twilio](https://www.twilio.com/try-twilio)
2. From the [Console](https://console.twilio.com), note your:
   - **Account SID** (starts with `AC`)
   - **Auth Token**
3. Get a free phone number from **Phone Numbers → Manage → Buy a number**
4. With a free trial account, you can only send SMS to **verified caller IDs** (numbers you've verified in the console)

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/twiliosmssender/
│   │   ├── TwilioSmsSenderApplication.java   # Spring Boot entry point
│   │   ├── config/
│   │   │   └── TwilioConfig.java             # Twilio SDK initialization
│   │   ├── controller/
│   │   │   └── SmsController.java            # REST endpoints
│   │   ├── domain/
│   │   │   ├── SmsMessage.java               # JPA entity (SMS audit record)
│   │   │   └── SmsStatus.java                # Enum: QUEUED, SENT, DELIVERED, FAILED, ...
│   │   ├── dto/
│   │   │   ├── SendSmsRequest.java           # Request body DTO
│   │   │   └── SmsResponse.java              # Response body DTO
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice (RFC 7807)
│   │   │   ├── SmsNotFoundException.java     # 404 exception
│   │   │   └── TwilioSmsException.java       # 502 exception (Twilio API error)
│   │   ├── repository/
│   │   │   └── SmsMessageRepository.java     # Spring Data JPA repository
│   │   └── service/
│   │       └── SmsService.java               # Business logic + Twilio API calls
│   └── resources/
│       └── application.yml                   # App configuration
└── test/
    ├── java/com/example/twiliosmssender/
    │   ├── domain/
    │   │   └── SmsMessageTest.java           # Unit tests: entity + lifecycle callbacks
    │   ├── service/
    │   │   └── SmsServiceTest.java           # Unit tests: service + status mapping
    │   └── integration/
    │       └── SmsMessageRepositoryIntegrationTest.java  # Integration tests (Testcontainers)
    └── resources/
        ├── docker-java.properties            # Docker API version override (Desktop 29+)
        └── testcontainers.properties         # Testcontainers Docker API config
```

---

## Running with Docker Compose

This is the recommended way to run the full application stack.

### Step 1 – Set your Twilio credentials

Create a `.env` file in the project root (never commit this file):

```bash
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=your_auth_token_here
TWILIO_PHONE_NUMBER=+15551234567
```

Or export them as environment variables:

```bash
export TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export TWILIO_AUTH_TOKEN=your_auth_token_here
export TWILIO_PHONE_NUMBER=+15551234567
```

### Step 2 – Build and start all services

```bash
docker compose up --build
```

This starts:
- `twiliosmssender-db` – PostgreSQL 16 on port `5432`
- `twiliosmssender-app` – Spring Boot app on port `8080`

### Step 3 – Stop and remove containers

```bash
docker compose down
```

To also remove the PostgreSQL data volume:

```bash
docker compose down -v
```

---

## API Endpoints

| Method | Path                        | Description                                  |
|--------|-----------------------------|----------------------------------------------|
| POST   | `/api/sms/send`             | Send an SMS via Twilio                       |
| GET    | `/api/sms`                  | List all SMS records from local DB           |
| GET    | `/api/sms/{id}`             | Get a message by local database ID           |
| GET    | `/api/sms/twilio/{sid}`     | Get a message by Twilio SID                  |
| GET    | `/api/sms/status/{status}`  | Get messages filtered by delivery status     |

---

## Usage Examples (curl)

### Send an SMS

```bash
curl -X POST http://localhost:8080/api/sms/send \
     -H "Content-Type: application/json" \
     -d '{"to": "+15551234567", "body": "Hello from Spring Boot + Twilio!"}'
```

**Response (HTTP 201 Created):**
```json
{
  "id": 1,
  "twilioSid": "SMxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "toNumber": "+15551234567",
  "fromNumber": "+15559876543",
  "body": "Hello from Spring Boot + Twilio!",
  "status": "QUEUED",
  "errorCode": null,
  "errorMessage": null,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

### List all SMS messages

```bash
curl http://localhost:8080/api/sms
```

### Get a message by local database ID

```bash
curl http://localhost:8080/api/sms/1
```

### Get a message by Twilio SID

```bash
curl http://localhost:8080/api/sms/twilio/SMxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### Get messages by delivery status

Valid status values: `QUEUED`, `SENDING`, `SENT`, `DELIVERED`, `FAILED`, `UNDELIVERED`

```bash
curl http://localhost:8080/api/sms/status/DELIVERED
curl http://localhost:8080/api/sms/status/FAILED
```

### Validation error example (HTTP 400)

```bash
curl -X POST http://localhost:8080/api/sms/send \
     -H "Content-Type: application/json" \
     -d '{"to": "not-a-phone-number", "body": "Test"}'
```

**Response (HTTP 400 Bad Request):**
```json
{
  "type": "https://example.com/errors/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "to: to must be a valid E.164 phone number (e.g. +15551234567)"
}
```

---

## SMS Status Lifecycle

```
POST /api/sms/send
       │
       ▼
   [QUEUED]  ← record saved locally before calling Twilio
       │
       ▼ (Twilio API call)
   [QUEUED] → [SENDING] → [SENT] → [DELIVERED]
                                  ↘ [UNDELIVERED]
       └──────────────────────────→ [FAILED]  (if Twilio rejects the request)
```

Twilio status values are mapped to the local `SmsStatus` enum:

| Twilio Status  | Local SmsStatus |
|----------------|-----------------|
| `queued`       | `QUEUED`        |
| `sending`      | `SENDING`       |
| `sent`         | `SENT`          |
| `delivered`    | `DELIVERED`     |
| `failed`       | `FAILED`        |
| `undelivered`  | `UNDELIVERED`   |
| *(other)*      | `QUEUED`        |

---

## Running the Tests

Tests require **Docker** to be running (for the Testcontainers integration tests).

```bash
./mvnw clean test
```

### Test categories

#### Unit Tests (no Docker required within the test JVM)
- **`SmsMessageTest`** – tests the JPA entity constructor, `@PrePersist`/`@PreUpdate` lifecycle callbacks, and all setters
- **`SmsServiceTest`** – tests the service layer logic including:
  - `mapTwilioStatus()` – pure function mapping Twilio status strings to enum values
  - `sendSms()` – success path and Twilio API failure path (using Mockito spy to avoid real HTTP calls)
  - `listMessages()`, `getMessageById()`, `getMessageByTwilioSid()`, `getMessagesByStatus()` – with mocked repository

#### Integration Tests (Testcontainers – requires Docker)
- **`SmsMessageRepositoryIntegrationTest`** – tests all Spring Data JPA repository methods against a **real PostgreSQL container**:
  - Save and retrieve by ID
  - `findByTwilioSid()` – found and not found
  - `findByStatus()` – filtered results and empty result
  - `findByToNumber()` – recipient phone number lookup
  - `existsByTwilioSid()` – boolean existence check
  - Status update persistence
  - Error field persistence (`errorCode`, `errorMessage`)
  - `@PrePersist` timestamp assignment

---

## Environment Variables Reference

| Variable               | Description                                      | Default (placeholder)            |
|------------------------|--------------------------------------------------|----------------------------------|
| `TWILIO_ACCOUNT_SID`   | Your Twilio Account SID (starts with `AC`)       | `ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` |
| `TWILIO_AUTH_TOKEN`    | Your Twilio Auth Token                           | `your_auth_token_here`           |
| `TWILIO_PHONE_NUMBER`  | Twilio sender phone number (E.164 format)        | `+15550000000`                   |
| `SPRING_DATASOURCE_URL`| JDBC URL (overridden by Docker Compose)          | `jdbc:postgresql://localhost:5432/twiliosmsdb` |
| `SPRING_DATASOURCE_USERNAME` | Database username                          | `twiliouser`                     |
| `SPRING_DATASOURCE_PASSWORD` | Database password                          | `twiliopass`                     |

---

## Phone Number Format

All phone numbers must be in **E.164 international format**:
- Starts with `+` followed by the country code
- No spaces, dashes, or parentheses
- Examples: `+15551234567` (US), `+441234567890` (UK), `+5491112345678` (Argentina)

---

## Security Notes

- **Never commit real Twilio credentials** to version control
- Use the `.env` file or environment variables to inject secrets
- The `.env` file is listed in `.gitignore`
- In production, use a secrets manager (AWS Secrets Manager, HashiCorp Vault, etc.)
