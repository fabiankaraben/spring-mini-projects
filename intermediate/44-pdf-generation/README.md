# PDF Generation — Spring Boot Mini-Project

A Spring Boot REST API that generates **downloadable PDF documents** from invoice data using the [OpenPDF](https://github.com/LibrePDF/OpenPDF) library (a free, LGPL/MPL-licensed fork of iText 2.x). Invoice metadata is persisted to **PostgreSQL**, and every invoice can be downloaded as a styled PDF at any time.

---

## What This Project Demonstrates

- Generating PDF files in memory using **OpenPDF** (`Document`, `PdfWriter`, `PdfPTable`, `Font`, etc.)
- Streaming PDF bytes back to the HTTP client with the correct `Content-Type: application/pdf` and `Content-Disposition: attachment` headers
- Persisting invoice metadata (number, customer, line items, totals) to **PostgreSQL** via **Spring Data JPA**
- Full **Bean Validation** (`@Valid`, `@NotBlank`, `@Email`, `@Min`, etc.) on request DTOs
- Centralised error handling with `@RestControllerAdvice`
- **Unit tests** (JUnit 5 + Mockito) that run in-memory with no external dependencies
- **Integration tests** using **Testcontainers** that spin up a real PostgreSQL Docker container

---

## Requirements

| Tool | Minimum version |
|------|----------------|
| Java | 21 |
| Maven (via wrapper) | 3.9+ |
| Docker Desktop | 4.x+ (for Docker Compose and Testcontainers) |

---

## Project Structure

```
src/
├── main/java/com/example/pdfgeneration/
│   ├── PdfGenerationApplication.java      # Spring Boot entry point
│   ├── controller/
│   │   └── InvoiceController.java         # REST endpoints
│   ├── domain/
│   │   ├── Invoice.java                   # JPA entity – invoice header
│   │   └── InvoiceLineItem.java           # JPA entity – line items
│   ├── dto/
│   │   ├── InvoiceRequest.java            # Request DTO (with validation)
│   │   ├── InvoiceLineItemRequest.java    # Nested line item DTO
│   │   └── InvoiceResponse.java           # Response DTO
│   ├── exception/
│   │   └── GlobalExceptionHandler.java    # @RestControllerAdvice
│   ├── repository/
│   │   ├── InvoiceRepository.java
│   │   └── InvoiceLineItemRepository.java
│   └── service/
│       ├── InvoiceService.java            # Business logic
│       └── PdfGeneratorService.java       # OpenPDF document assembly
└── test/java/com/example/pdfgeneration/
    ├── unit/
    │   ├── PdfGeneratorServiceTest.java   # Unit tests – PDF output
    │   └── InvoiceServiceTest.java        # Unit tests – business logic
    └── integration/
        └── InvoiceIntegrationTest.java    # Integration tests (Testcontainers)
```

---

## Running the Application with Docker Compose

The entire project (Spring Boot app + PostgreSQL) is managed by Docker Compose.

### 1. Start all services

```bash
docker compose up --build
```

This command:
1. Builds the Spring Boot fat JAR inside a Docker build stage.
2. Starts a `postgres:16-alpine` container with a health check.
3. Waits for PostgreSQL to be healthy, then starts the application container.

The API is then available at **http://localhost:8080**.

### 2. Stop all services

```bash
docker compose down
```

### 3. Stop and remove volumes (wipes the database)

```bash
docker compose down -v
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/invoices` | Create a new invoice (returns metadata + PDF download URL) |
| `GET`  | `/api/invoices` | List all invoices (metadata only) |
| `GET`  | `/api/invoices/{id}` | Get a single invoice by id |
| `GET`  | `/api/invoices/{id}/pdf` | **Download the invoice as a PDF file** |
| `GET`  | `/api/invoices/report/pdf` | **Download a summary report PDF of all invoices** |

---

## Usage Examples (curl)

### Create an invoice

```bash
curl -s -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceNumber": "INV-2024-001",
    "customerName": "Acme Corporation",
    "customerEmail": "billing@acme.com",
    "issueDate": "2024-06-15",
    "currency": "USD",
    "notes": "Payment due within 30 days. Thank you for your business.",
    "lineItems": [
      { "description": "Backend Development",   "quantity": 20, "unitPrice": 120.00 },
      { "description": "Frontend Development",  "quantity": 15, "unitPrice": 100.00 },
      { "description": "DevOps Setup",          "quantity":  5, "unitPrice": 180.00 },
      { "description": "Project Management",    "quantity":  8, "unitPrice":  90.00 }
    ]
  }' | jq .
```

**Response (`201 Created`):**
```json
{
  "id": 1,
  "invoiceNumber": "INV-2024-001",
  "customerName": "Acme Corporation",
  "customerEmail": "billing@acme.com",
  "totalAmount": 5020.00,
  "currency": "USD",
  "issueDate": "2024-06-15",
  "downloadUrl": "/api/invoices/1/pdf"
}
```

### Download an invoice PDF

```bash
curl -o invoice-1.pdf http://localhost:8080/api/invoices/1/pdf
```

### Download a summary report PDF

```bash
curl -o invoice-report.pdf http://localhost:8080/api/invoices/report/pdf
```

### List all invoices

```bash
curl -s http://localhost:8080/api/invoices | jq .
```

### Get a single invoice by id

```bash
curl -s http://localhost:8080/api/invoices/1 | jq .
```

### Validation error example

```bash
curl -s -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -d '{
    "invoiceNumber": "",
    "customerName": "Acme Corp",
    "customerEmail": "not-an-email",
    "issueDate": "2024-06-15",
    "lineItems": []
  }' | jq .
```

**Response (`400 Bad Request`):**
```json
{
  "timestamp": "2024-06-15T12:00:00Z",
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more fields failed validation",
  "fieldErrors": {
    "invoiceNumber": "Invoice number must not be blank",
    "customerEmail": "Customer email must be a valid email address",
    "lineItems": "At least one line item is required"
  }
}
```

---

## Running the Tests

### Prerequisites

- Docker Desktop must be **running** before executing the tests (required by Testcontainers to spin up the PostgreSQL container).

### Run all tests

```bash
./mvnw clean test
```

### Run only unit tests

```bash
./mvnw test -Dtest="**/unit/**"
```

### Run only integration tests

```bash
./mvnw test -Dtest="**/integration/**"
```

---

## Test Overview

### Unit Tests (`src/test/java/.../unit/`)

These tests run entirely in-memory with no Spring context, no database, and no Docker containers. They are fast and suitable for TDD.

| Test class | What it tests |
|-----------|--------------|
| `PdfGeneratorServiceTest` | Verifies that `PdfGeneratorService` produces valid, non-empty byte arrays starting with the PDF magic bytes (`%PDF`). Tests edge cases: empty line items, null/blank notes, multiple items, report PDF. Also tests `InvoiceLineItem.getSubtotal()` domain logic. |
| `InvoiceServiceTest` | Verifies `InvoiceService` business logic using Mockito mocks for all repositories and `PdfGeneratorService`. Tests: correct DTO mapping, total amount computation, duplicate invoice number rejection, line item persistence count, PDF delegation, not-found handling. |

### Integration Tests (`src/test/java/.../integration/`)

| Test class | What it tests |
|-----------|--------------|
| `InvoiceIntegrationTest` | Full stack tests using Testcontainers (real PostgreSQL 16) + MockMvc. Covers all 5 endpoints: create invoice (happy path + validation errors + duplicate), list all, get by id (found + 404), download invoice PDF (valid bytes + 404), download report PDF. |

---

## How PDF Generation Works

1. The client sends a `POST /api/invoices` request with invoice data.
2. `InvoiceService` validates the business rules and persists the `Invoice` and `InvoiceLineItem` entities to PostgreSQL.
3. When the client requests `GET /api/invoices/{id}/pdf`, `InvoiceService` loads the entity and its line items, then delegates to `PdfGeneratorService`.
4. `PdfGeneratorService` uses **OpenPDF** to build a `Document` in memory:
   - Title bar with invoice number
   - Two-column info block (customer details on the left, invoice date on the right)
   - Styled line-items table (Description | Qty | Unit Price | Subtotal)
   - Right-aligned totals section
   - Optional notes footer
5. The resulting `byte[]` is returned to the controller, which sets `Content-Type: application/pdf` and `Content-Disposition: attachment` before streaming it to the client.

---

## Key Dependencies

| Dependency | Purpose |
|-----------|---------|
| `spring-boot-starter-web` | REST controllers, Jackson JSON |
| `spring-boot-starter-data-jpa` | JPA/Hibernate ORM layer |
| `spring-boot-starter-validation` | Bean Validation (`@Valid`, `@NotBlank`, etc.) |
| `com.github.librepdf:openpdf` | PDF generation (free fork of iText 2.x, LGPL/MPL) |
| `postgresql` | JDBC driver for PostgreSQL |
| `spring-boot-starter-test` | JUnit 5, AssertJ, Mockito, MockMvc |
| `testcontainers:junit-jupiter` | JUnit 5 integration for Testcontainers |
| `testcontainers:postgresql` | PostgreSQL Docker container for integration tests |
