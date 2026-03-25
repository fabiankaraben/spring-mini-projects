# Serverless Spring Function

A Spring Boot mini-project demonstrating how to package business logic as **functions** deployable to **AWS Lambda** using [Spring Cloud Function](https://spring.io/projects/spring-cloud-function).

The same function code runs:
- **Locally** as an HTTP server via `spring-cloud-function-web` (no code changes).
- **On AWS Lambda** by swapping in the `spring-cloud-function-adapter-aws` module.

---

## What This Project Demonstrates

| Concept | Description |
|---|---|
| **Spring Cloud Function** | Business logic as plain `Function<T,R>`, `Consumer<T>` Java beans |
| **Automatic HTTP exposure** | Each bean is available at `POST /{beanName}` with zero boilerplate |
| **AWS Lambda adapter** | Same beans run on Lambda via `FunctionInvoker` handler |
| **Function composition** | Chain functions at the HTTP layer: `POST /calculateTax,applyDiscount` |
| **Domain logic** | Tax calculation + discount application + invoice generation |

---

## Domain: Invoice Processing

The project models an **e-commerce invoice processing pipeline** with four functions:

| Bean | Type | Endpoint | Description |
|---|---|---|---|
| `calculateTax` | `Function<OrderRequest, TaxResult>` | `POST /calculateTax` | Computes tax for an order using country/state rates |
| `applyDiscount` | `Function<DiscountRequest, DiscountResult>` | `POST /applyDiscount` | Applies a promotional discount code |
| `generateInvoice` | `Function<InvoiceRequest, Invoice>` | `POST /generateInvoice` | Produces a full invoice (tax + discount combined) |
| `auditLogger` | `Consumer<AuditEvent>` | `POST /auditLogger` | Logs audit events — returns `202 Accepted` |

### Tax Rates

| Country / State | Rate |
|---|---|
| US / CA (California) | 8.75% |
| US / NY (New York) | 8.00% |
| US (default) | 7.00% |
| DE (Germany VAT) | 19.00% |
| GB (UK VAT) | 20.00% |
| AU (Australia GST) | 10.00% |
| All others | 5.00% |

### Discount Codes

| Code | Discount |
|---|---|
| `SAVE10` | 10% off |
| `SAVE20` | 20% off |
| `HALFOFF` | 50% off |
| `WELCOME5` | 5% off |
| Any other | 0% (no error) |

---

## Requirements

- **Java 21+**
- **Maven** (or use the included `./mvnw` wrapper — no local Maven needed)
- **Docker** (required for Docker Compose and Testcontainers integration tests)

---

## Running Locally (without Docker)

```bash
./mvnw spring-boot:run
```

The application starts on **port 8080**. All four function endpoints are available immediately.

---

## Running with Docker Compose

Build the image and start the container:

```bash
docker compose up --build
```

Run in the background (detached mode):

```bash
docker compose up --build -d
```

Check application logs:

```bash
docker compose logs -f
```

Stop and remove containers:

```bash
docker compose down
```

The application is accessible at `http://localhost:8080` once the health check passes.

---

## API Usage (curl Examples)

### POST /calculateTax

Calculate tax for a California order:

```bash
curl -s -X POST http://localhost:8080/calculateTax \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "customerId": "CUST-42",
    "subtotal": "199.99",
    "country": "US",
    "state": "CA"
  }' | jq .
```

Expected response:
```json
{
  "orderId": "ORD-001",
  "subtotal": 199.99,
  "taxRate": 0.0875,
  "taxAmount": 17.50,
  "total": 217.49
}
```

Calculate tax for a German order (19% VAT):

```bash
curl -s -X POST http://localhost:8080/calculateTax \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-002",
    "customerId": "CUST-10",
    "subtotal": "100.00",
    "country": "DE"
  }' | jq .
```

---

### POST /applyDiscount

Apply a `SAVE10` discount code:

```bash
curl -s -X POST http://localhost:8080/applyDiscount \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "originalTotal": "217.49",
    "discountCode": "SAVE10"
  }' | jq .
```

Expected response:
```json
{
  "orderId": "ORD-001",
  "originalTotal": 217.49,
  "discountCode": "SAVE10",
  "discountPercent": 10.00,
  "discountAmount": 21.75,
  "finalTotal": 195.74
}
```

Apply a `HALFOFF` discount:

```bash
curl -s -X POST http://localhost:8080/applyDiscount \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-003",
    "originalTotal": "100.00",
    "discountCode": "HALFOFF"
  }' | jq .
```

---

### POST /generateInvoice

Generate a full invoice (tax + discount in one call):

```bash
curl -s -X POST http://localhost:8080/generateInvoice \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "customerId": "CUST-42",
    "subtotal": "199.99",
    "country": "US",
    "state": "CA",
    "discountCode": "SAVE10"
  }' | jq .
```

Expected response:
```json
{
  "invoiceId": "INV-ORD-001-1712345678",
  "orderId": "ORD-001",
  "customerId": "CUST-42",
  "subtotal": 199.99,
  "taxRate": 0.0875,
  "taxAmount": 17.50,
  "totalBeforeDiscount": 217.49,
  "discountCode": "SAVE10",
  "discountPercent": 10.00,
  "discountAmount": 21.75,
  "finalTotal": 195.74,
  "issuedAt": "2024-04-05T12:34:56Z"
}
```

Generate an invoice without a discount code:

```bash
curl -s -X POST http://localhost:8080/generateInvoice \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-002",
    "customerId": "CUST-10",
    "subtotal": "500.00",
    "country": "GB"
  }' | jq .
```

---

### POST /auditLogger

Log an audit event (Consumer — returns `202 Accepted`, no response body):

```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/auditLogger \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "INVOICE_GENERATED",
    "orderId": "ORD-001",
    "actor": "invoice-service",
    "details": "Invoice generated for customer CUST-42"
  }'
```

Expected output: `202`

---

### Function Composition (chaining)

Spring Cloud Function supports chaining two compatible functions at the HTTP layer using a comma-separated path:

```bash
# This chains calculateTax → applyDiscount:
# - calculateTax returns TaxResult (which has a "total" field)
# - Note: direct composition only works when output type matches input type.
# Use generateInvoice for the combined result in this project.
```

> **Note:** For the combined tax + discount pipeline, use `POST /generateInvoice` — it's the purpose-built composed function that takes a single `InvoiceRequest` and returns a complete `Invoice`.

---

### Health Check

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

---

## AWS Lambda Deployment

The project includes the `spring-cloud-function-adapter-aws` dependency (`provided` scope), which means it compiles but is not included in the default fat-jar. To deploy to real AWS Lambda:

1. **Build the deployment jar** (with the AWS adapter bundled):
   ```bash
   ./mvnw package -DskipTests
   ```

2. **Create a Lambda function** via AWS CLI:
   ```bash
   aws lambda create-function \
     --function-name calculateTax \
     --runtime java21 \
     --handler org.springframework.cloud.function.adapter.aws.FunctionInvoker \
     --role arn:aws:iam::YOUR_ACCOUNT:role/lambda-execution-role \
     --zip-file fileb://target/serverless-spring-function-0.0.1-SNAPSHOT.jar \
     --timeout 30 \
     --memory-size 512 \
     --environment "Variables={SPRING_CLOUD_FUNCTION_DEFINITION=calculateTax}"
   ```

3. **Invoke the Lambda function**:
   ```bash
   aws lambda invoke \
     --function-name calculateTax \
     --payload '{"orderId":"ORD-001","customerId":"CUST-42","subtotal":199.99,"country":"US","state":"CA"}' \
     --cli-binary-format raw-in-base64-out \
     response.json && cat response.json
   ```

The `SPRING_CLOUD_FUNCTION_DEFINITION` environment variable tells the Spring Cloud Function adapter **which bean** to activate on Lambda. Change it to `generateInvoice`, `applyDiscount`, or `auditLogger` to switch function.

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/serverless/
│   │   ├── ServerlessFunctionApplication.java   # Spring Boot entry point
│   │   ├── domain/                              # Input/output DTOs
│   │   │   ├── OrderRequest.java
│   │   │   ├── TaxResult.java
│   │   │   ├── DiscountRequest.java
│   │   │   ├── DiscountResult.java
│   │   │   ├── InvoiceRequest.java
│   │   │   ├── Invoice.java
│   │   │   └── AuditEvent.java
│   │   ├── service/                             # Domain business logic
│   │   │   ├── TaxService.java
│   │   │   ├── DiscountService.java
│   │   │   └── InvoiceService.java
│   │   └── function/                            # Spring Cloud Function beans
│   │       └── InvoiceFunctions.java            # @Bean definitions (Function/Consumer)
│   └── resources/
│       └── application.yml
└── test/
    ├── java/com/example/serverless/
    │   ├── service/                             # JUnit 5 unit tests (no Spring context)
    │   │   ├── TaxServiceTest.java
    │   │   ├── DiscountServiceTest.java
    │   │   └── InvoiceServiceTest.java
    │   └── integration/                         # Integration tests
    │       ├── FunctionHttpIntegrationTest.java      # Full Spring context + MockMvc
    │       └── LocalStackLambdaIntegrationTest.java  # Testcontainers + LocalStack
    └── resources/
        ├── docker-java.properties               # Fixes Docker Desktop 29+ API version
        └── testcontainers.properties            # Complements docker-java.properties
```

---

## Running the Tests

### All tests (unit + integration)

```bash
./mvnw clean test
```

### Unit tests only (fast, no Docker required)

```bash
./mvnw test -Dtest="TaxServiceTest,DiscountServiceTest,InvoiceServiceTest"
```

### Integration tests only

```bash
./mvnw test -Dtest="FunctionHttpIntegrationTest,LocalStackLambdaIntegrationTest"
```

> **Note on `LocalStackLambdaIntegrationTest`:** This test deploys the fat-jar to LocalStack (a local AWS Lambda simulator running in Docker). It requires the fat-jar to exist in `target/`. If the jar is not present (e.g., after a clean without package), the Lambda deployment tests are automatically **skipped** with an informational message — all other tests still run. To ensure the jar is available:
> ```bash
> ./mvnw package -DskipTests && ./mvnw test
> ```

### Test categories

| Test Class | Type | Docker? | What it tests |
|---|---|---|---|
| `TaxServiceTest` | Unit | No | Tax rate lookup, calculation, rounding |
| `DiscountServiceTest` | Unit | No | Discount code lookup, amount computation |
| `InvoiceServiceTest` | Unit | No | End-to-end invoice orchestration |
| `FunctionHttpIntegrationTest` | Integration | No | All 4 HTTP endpoints via MockMvc |
| `LocalStackLambdaIntegrationTest` | Integration | **Yes** | Lambda deployment + invocation via LocalStack |

---

## Key Concepts Explained

### Spring Cloud Function bean discovery

Any `@Bean` of type `java.util.function.Function`, `Consumer`, or `Supplier` is automatically discovered by Spring Cloud Function's `BeanFactoryAwareFunctionRegistry`. When `spring-cloud-function-web` is on the classpath, each bean is exposed at `POST /{beanName}`.

### Why `spring.cloud.function.definition`?

When multiple Function/Consumer beans exist, Spring Cloud Function requires an explicit list to avoid ambiguity:

```yaml
spring:
  cloud:
    function:
      definition: calculateTax;applyDiscount;generateInvoice;auditLogger
```

### Consumer vs Function HTTP response

- `Function<T,R>` → HTTP `200 OK` with the return value as JSON body
- `Consumer<T>` → HTTP `202 Accepted` with empty body (fire-and-forget)

### Lambda cold start optimization

The Spring Boot application context is initialized once per Lambda container and cached for subsequent invocations. Use `@Lazy` beans or `spring.main.lazy-initialization=true` to reduce cold start time in production.
