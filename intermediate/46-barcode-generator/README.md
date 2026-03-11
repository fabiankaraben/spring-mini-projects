# Barcode Generator

A Spring Boot backend that generates **QR codes** and **1-D barcodes** on demand and returns them as PNG image streams. No database required — images are generated on-the-fly using [ZXing (Zebra Crossing)](https://github.com/zxing/zxing), Google's open-source barcode library.

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use the included Maven Wrapper `./mvnw`) |
| Docker | Required to run via Docker Compose |
| Docker Compose | V2 (`docker compose` command) |

---

## Supported Formats

| Format | Description | Content Rules |
|---|---|---|
| `QR_CODE` | 2-D matrix code (default) | Any text, URL, or binary data |
| `CODE_128` | High-density 1-D barcode | All 128 ASCII characters |
| `EAN_13` | 13-digit retail barcode | Exactly **12 numeric digits** (13th check digit is computed automatically) |
| `UPC_A` | 12-digit North-American retail | Exactly **11 numeric digits** (12th check digit is computed automatically) |
| `CODE_39` | Variable-length 1-D barcode | Uppercase letters, digits 0-9, and `- . $ / + % SPACE` |
| `PDF_417` | 2-D stacked barcode | Any text (used on boarding passes, ID documents) |

---

## API Reference

### `GET /api/barcode`

Generates a barcode or QR code in the specified format.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `content` | string | ✅ | — | Data to encode |
| `format` | enum | ❌ | `QR_CODE` | One of the supported formats above |
| `width` | int | ❌ | `300` | Image width in pixels (10–2000) |
| `height` | int | ❌ | `300` | Image height in pixels (10–2000) |

**Response:** `200 OK` with `Content-Type: image/png`

---

### `GET /api/barcode/qr`

Convenience shortcut — always generates a QR code (no `format` parameter needed).

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `content` | string | ✅ | — | Data to encode |
| `width` | int | ❌ | `300` | Image width in pixels (10–2000) |
| `height` | int | ❌ | `300` | Image height in pixels (10–2000) |

**Response:** `200 OK` with `Content-Type: image/png`

---

## curl Examples

All examples save the PNG response to a local file. Open the file with any image viewer or browser.

### QR Code (URL)
```bash
curl -o qr.png "http://localhost:8080/api/barcode/qr?content=https://example.com"
```

### QR Code with custom size
```bash
curl -o qr_large.png "http://localhost:8080/api/barcode/qr?content=Hello+World&width=500&height=500"
```

### QR Code via the generic endpoint
```bash
curl -o qr_generic.png "http://localhost:8080/api/barcode?content=Hello+World&format=QR_CODE"
```

### Code 128 barcode
```bash
curl -o code128.png "http://localhost:8080/api/barcode?content=HELLO-WORLD-123&format=CODE_128&width=400&height=150"
```

### EAN-13 barcode (12 numeric digits)
```bash
curl -o ean13.png "http://localhost:8080/api/barcode?content=012345678901&format=EAN_13&width=300&height=150"
```

### UPC-A barcode (11 numeric digits)
```bash
curl -o upca.png "http://localhost:8080/api/barcode?content=01234567890&format=UPC_A&width=300&height=150"
```

### Code 39 barcode
```bash
curl -o code39.png "http://localhost:8080/api/barcode?content=CODE39TEST&format=CODE_39&width=400&height=150"
```

### PDF 417 barcode
```bash
curl -o pdf417.png "http://localhost:8080/api/barcode?content=Boarding+pass+data+1234&format=PDF_417&width=400&height=200"
```

### Error example – invalid format
```bash
curl -v "http://localhost:8080/api/barcode?content=test&format=INVALID"
# Returns: 400 Bad Request
```

### Error example – EAN-13 with non-numeric content
```bash
curl -v "http://localhost:8080/api/barcode?content=NOTANUMBER&format=EAN_13"
# Returns: 400 Bad Request
```

---

## Running Locally (without Docker)

```bash
./mvnw spring-boot:run
```

The application starts on port **8080**.

---

## Running with Docker Compose

This is the recommended way to run the application in a production-like environment.

### Build and start

```bash
docker compose up --build
```

### Start in the background (detached)

```bash
docker compose up --build -d
```

### Stop and remove containers

```bash
docker compose down
```

### View logs

```bash
docker compose logs -f app
```

Once running, the API is available at `http://localhost:8080`.

---

## Docker Details

### Dockerfile

A **multi-stage build** is used to keep the final image small:

1. **Build stage** (`eclipse-temurin:21-jdk-alpine`) – Downloads Maven dependencies and compiles the application into an executable JAR.
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`) – Copies only the JAR from the build stage. No JDK, no Maven, no source code in the final image.

### docker-compose.yml

Only one service is defined (`app`) because the barcode generator has no external dependencies (no database, no cache, no message broker). Images are generated entirely in memory.

---

## Running the Tests

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

## Test Structure

```
src/test/java/com/example/barcodegenerator/
├── unit/
│   └── BarcodeServiceTest.java          # JUnit 5 unit tests for the service layer
└── integration/
    └── BarcodeControllerIntegrationTest.java  # Full Spring MVC integration tests
```

### Unit Tests (`BarcodeServiceTest`)

Test `BarcodeService` in isolation — no Spring context loaded, instantiated via `new`. Cover:

- Every supported format produces a non-empty PNG byte array.
- Output bytes decode into a valid `BufferedImage` with correct dimensions.
- PNG magic bytes (`\x89PNG`) are present in every output.
- Larger requested dimensions produce a larger image.
- Invalid content for numeric-only formats (EAN-13, UPC-A) throws `BarcodeGenerationException`.

### Integration Tests (`BarcodeControllerIntegrationTest`)

Use `@SpringBootTest` + `MockMvc` to exercise the full Spring MVC stack. Cover:

- Every supported format returns `200 OK` with `Content-Type: image/png`.
- The convenience `/api/barcode/qr` endpoint works correctly.
- Default parameter values apply when optional parameters are omitted.
- Missing `content` parameter → `400 Bad Request`.
- Unknown `format` value → `400 Bad Request`.
- Width/height outside the 10–2000 pixel bounds → `400 Bad Request`.
- EAN-13 with non-numeric content → `400 Bad Request`.
- PNG magic bytes verified in every successful response body.

> **Note on Testcontainers:** Testcontainers is included as a dependency with Docker API properties pre-configured (`docker-java.properties`, `testcontainers.properties`). This application has no external services, so no container is started during tests. The configuration is in place so future tests requiring containers (e.g. a Redis cache) can be added without extra setup.

---

## Project Structure

```
src/main/java/com/example/barcodegenerator/
├── BarcodeGeneratorApplication.java     # Spring Boot entry point
├── controller/
│   ├── BarcodeController.java           # REST endpoints (/api/barcode, /api/barcode/qr)
│   └── GlobalExceptionHandler.java      # Centralised error handling (@RestControllerAdvice)
├── domain/
│   ├── BarcodeFormat.java               # Enum of supported formats
│   └── BarcodeRequest.java              # Immutable value object (record)
├── exception/
│   └── BarcodeGenerationException.java  # Unchecked exception for ZXing errors
└── service/
    └── BarcodeService.java              # Core ZXing encoding logic
```

---

## Technology Stack

| Technology | Role |
|---|---|
| Spring Boot 3.4 | Application framework |
| Spring Web (MVC) | REST controllers and HTTP handling |
| Bean Validation (Hibernate Validator) | Request parameter validation |
| ZXing (Zebra Crossing) 3.5.3 | Barcode / QR code encoding |
| JUnit 5 | Unit and integration testing |
| AssertJ | Fluent assertions in tests |
| Testcontainers | Integration test infrastructure |
| Docker / Docker Compose | Containerised deployment |
