# Stripe Payment API

A Spring Boot backend that integrates with the **Stripe Java SDK** to process payments using the PaymentIntent API. This mini-project demonstrates how to create, confirm, cancel, and retrieve payments, while persisting a local audit trail in PostgreSQL.

## What this project covers

- **Stripe PaymentIntent lifecycle** – create, confirm, cancel
- **Local payment persistence** – every PaymentIntent is mirrored in PostgreSQL
- **RESTful API** – clean endpoints following HTTP conventions
- **Bean Validation** – request body validation with informative error responses
- **RFC 7807 Problem Details** – consistent JSON error responses
- **Unit tests** – JUnit 5 + Mockito, no database or Docker required
- **Integration tests** – Testcontainers spins up a real PostgreSQL container
- **Docker Compose** – the entire stack (app + PostgreSQL) runs in Docker

## Requirements

| Tool | Minimum version |
|------|----------------|
| Java | 21 |
| Maven | 3.9+ (via Maven Wrapper) |
| Docker Desktop | 4.x |
| Stripe account | Free test account |

> A **Stripe test API key** (`sk_test_...`) is required to make real API calls. Get one for free at [https://dashboard.stripe.com/test/apikeys](https://dashboard.stripe.com/test/apikeys). For running tests, a placeholder key is sufficient because tests mock the Stripe layer.

## Project structure

```
src/
├── main/java/com/example/stripepayment/
│   ├── StripePaymentApplication.java       # Spring Boot entry point
│   ├── config/
│   │   └── StripeConfig.java               # Initializes Stripe SDK with API key
│   ├── controller/
│   │   └── PaymentController.java          # REST endpoints (/api/payments/*)
│   ├── domain/
│   │   ├── Payment.java                    # JPA entity (payments table)
│   │   └── PaymentStatus.java              # Enum: PENDING, SUCCEEDED, CANCELED, FAILED
│   ├── dto/
│   │   ├── CreatePaymentRequest.java       # Request body DTO
│   │   └── PaymentResponse.java            # Response body DTO
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java     # @RestControllerAdvice, RFC 7807
│   │   ├── PaymentNotFoundException.java   # → 404
│   │   └── StripePaymentException.java     # → 502
│   ├── repository/
│   │   └── PaymentRepository.java          # Spring Data JPA repository
│   └── service/
│       └── PaymentService.java             # Stripe API calls + DB persistence
└── test/java/com/example/stripepayment/
    ├── domain/
    │   └── PaymentTest.java                # Domain entity unit tests
    ├── integration/
    │   └── PaymentRepositoryIntegrationTest.java  # Testcontainers (PostgreSQL)
    └── service/
        └── PaymentServiceTest.java         # Service unit tests (Mockito)
```

## Running with Docker Compose

The entire stack (Spring Boot application + PostgreSQL database) is orchestrated with Docker Compose.

### 1. Set your Stripe test API key

```bash
export STRIPE_API_KEY=sk_test_YOUR_KEY_HERE
```

> Never commit your API key. The `docker-compose.yml` reads it from the `STRIPE_API_KEY` environment variable.

### 2. Start the stack

```bash
docker compose up --build
```

This will:
1. Build the Spring Boot application JAR inside a Docker build container
2. Start a PostgreSQL 16 container (waits until healthy)
3. Start the application container (waits for PostgreSQL to be ready)

The API will be available at `http://localhost:8080`.

### 3. Stop the stack

```bash
docker compose down
```

To also remove the PostgreSQL data volume:

```bash
docker compose down -v
```

## Running locally (without Docker)

You need a running PostgreSQL instance. The easiest way:

```bash
docker compose up db -d
```

Then run the application:

```bash
export STRIPE_API_KEY=sk_test_YOUR_KEY_HERE
./mvnw spring-boot:run
```

## API endpoints

### Create a PaymentIntent

```bash
curl -X POST http://localhost:8080/api/payments \
     -H "Content-Type: application/json" \
     -d '{
       "amount": 2000,
       "currency": "usd",
       "description": "Order #1234 – Spring Boot T-Shirt"
     }'
```

> `amount` is in the **smallest currency unit** (e.g., 2000 = $20.00 USD).

Response:
```json
{
  "id": 1,
  "stripePaymentIntentId": "pi_3NtYvJ2eZvKYlo2C1kfmXjBR",
  "amount": 2000,
  "currency": "usd",
  "description": "Order #1234 – Spring Boot T-Shirt",
  "status": "PENDING",
  "clientSecret": "pi_3NtYvJ2eZvKYlo2C1kfmXjBR_secret_...",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

### List all payments

```bash
curl http://localhost:8080/api/payments
```

### Get a payment by local ID

```bash
curl http://localhost:8080/api/payments/1
```

### Get a payment by Stripe PaymentIntent ID

```bash
curl http://localhost:8080/api/payments/stripe/pi_3NtYvJ2eZvKYlo2C1kfmXjBR
```

### Confirm a PaymentIntent

Simulates the frontend confirming the payment using a Stripe test card.

```bash
curl -X POST http://localhost:8080/api/payments/pi_3NtYvJ2eZvKYlo2C1kfmXjBR/confirm
```

### Cancel a PaymentIntent

```bash
curl -X POST http://localhost:8080/api/payments/pi_3NtYvJ2eZvKYlo2C1kfmXjBR/cancel
```

## Full lifecycle example

```bash
# 1. Create a payment
RESPONSE=$(curl -s -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"amount": 5000, "currency": "usd", "description": "Test payment"}')

echo $RESPONSE | python3 -m json.tool

# 2. Extract the Stripe PaymentIntent ID
STRIPE_ID=$(echo $RESPONSE | python3 -c "import sys,json; print(json.load(sys.stdin)['stripePaymentIntentId'])")

# 3. Confirm the payment
curl -s -X POST http://localhost:8080/api/payments/$STRIPE_ID/confirm | python3 -m json.tool

# 4. Verify the status is SUCCEEDED
curl -s http://localhost:8080/api/payments/stripe/$STRIPE_ID | python3 -m json.tool
```

## Error responses (RFC 7807 Problem Details)

All errors follow the [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) format:

```json
{
  "type": "/errors/payment-not-found",
  "title": "Payment Not Found",
  "status": 404,
  "detail": "Payment not found with id: 999"
}
```

**Validation error** (400):
```bash
curl -X POST http://localhost:8080/api/payments \
     -H "Content-Type: application/json" \
     -d '{"amount": -1, "currency": "us"}'
```

```json
{
  "type": "/errors/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "Request validation failed",
  "fieldErrors": {
    "amount": "amount must be at least 1 (smallest currency unit)",
    "currency": "currency must be a 3-letter ISO 4217 code (e.g. usd)"
  }
}
```

## Running the tests

### Unit tests only (no Docker required)

```bash
./mvnw test -Dgroups=\!integration
```

### All tests (unit + integration via Testcontainers)

Docker must be running.

```bash
./mvnw clean test
```

### Test breakdown

| Test class | Type | What it tests |
|-----------|------|--------------|
| `PaymentTest` | Unit | `Payment` entity constructors, setters, `PaymentStatus` enum |
| `PaymentServiceTest` | Unit | `PaymentService` logic with mocked repository |
| `PaymentRepositoryIntegrationTest` | Integration (Testcontainers) | Spring Data JPA repository against real PostgreSQL |

> The integration tests use Testcontainers to spin up a real PostgreSQL 16 Docker container. The container is started once per test class and shared across all test methods for efficiency.

## Configuration reference

| Property | Default | Description |
|----------|---------|-------------|
| `stripe.api-key` | `${STRIPE_API_KEY:sk_test_placeholder}` | Stripe secret API key |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/stripedb` | JDBC connection URL |
| `spring.datasource.username` | `stripeuser` | Database username |
| `spring.datasource.password` | `stripepass` | Database password |
| `server.port` | `8080` | HTTP server port |

## Key concepts

### PaymentIntent lifecycle

```
[Create] → PENDING (requires_confirmation)
              ↓
[Confirm] → SUCCEEDED (payment captured)
              OR
[Cancel]  → CANCELED
```

### Stripe test cards

When calling the `/confirm` endpoint, the application uses Stripe's built-in test payment method `pm_card_visa`. In a real application, the card details are collected on the frontend using **Stripe.js**, which returns a secure `pm_xxx` token — no raw card numbers ever reach your server.

### Security note

- **Never log** the `clientSecret` field
- **Never commit** your `sk_live_...` key to version control
- Use environment variables or a secrets manager (e.g., AWS Secrets Manager, HashiCorp Vault) in production
