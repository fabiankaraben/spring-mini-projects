# 45 – Excel Export

A Spring Boot backend that generates and streams **Excel (.xlsx)** files using **Apache POI**.
Clients receive a ready-to-download spreadsheet from a simple HTTP request — no temporary files are written to disk.

---

## What this project demonstrates

| Concept | Details |
|---|---|
| **Apache POI XSSF API** | `XSSFWorkbook`, `Sheet`, `Row`, `Cell` |
| **Cell styles** | Custom fonts, background colours, borders, number formats |
| **Merged regions** | Title row spans all columns via `CellRangeAddress` |
| **Zebra striping** | Alternating row colours improve readability |
| **Frozen panes** | Title + header rows stay visible while scrolling |
| **Excel formulas** | `SUM` and `AVERAGE` in the totals row |
| **HTTP streaming** | Binary response with `Content-Disposition: attachment` |
| **Bean Validation** | `@Valid` on the request body rejects bad input with 400 |
| **RFC 9457 errors** | `ProblemDetail` responses for validation and server errors |

---

## Requirements

| Tool | Minimum version |
|---|---|
| Java | 21 |
| Maven | 3.9 (or use the included `./mvnw` wrapper) |
| Docker Desktop | 4.x (only needed for Docker Compose or integration tests) |

---

## Project structure

```
src/
├── main/java/com/example/excelexport/
│   ├── ExcelExportApplication.java      # @SpringBootApplication entry point
│   ├── controller/
│   │   └── ExcelExportController.java   # GET /api/export/products/sample
│   │                                    # POST /api/export/products
│   ├── exception/
│   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice – ProblemDetail
│   ├── model/
│   │   ├── Product.java                 # Domain record (id, name, category, price, stock)
│   │   └── ExcelReportRequest.java      # POST request body (title + products list)
│   └── service/
│       └── ExcelExportService.java      # Apache POI workbook generation logic
└── test/java/com/example/excelexport/
    ├── controller/
    │   └── ExcelExportControllerIntegrationTest.java  # Full HTTP integration tests
    └── service/
        └── ExcelExportServiceTest.java  # Unit tests for workbook generation
```

---

## Running locally (without Docker)

```bash
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## Running with Docker Compose

```bash
# Build the image and start the container
docker compose up --build

# Run in the background
docker compose up --build -d

# Stop and remove the container
docker compose down
```

The application is accessible at **http://localhost:8080** once the health check passes.

---

## API Endpoints

### `GET /api/export/products/sample`

Returns a pre-built sample workbook containing 10 demo products.
No request body required — great for quickly verifying the setup.

**Response headers**

```
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="sample-products.xlsx"
```

---

### `POST /api/export/products`

Accepts a JSON body and returns a customised workbook.

**Request body**

```json
{
  "reportTitle": "Q1 Inventory",
  "products": [
    {
      "id": "PRD-001",
      "name": "Laptop Pro 15",
      "category": "Electronics",
      "price": 1299.99,
      "stock": 45
    }
  ]
}
```

| Field | Type | Constraints |
|---|---|---|
| `reportTitle` | `string` | Not blank |
| `products` | `array` | At least one item |
| `products[].id` | `string` | Not blank |
| `products[].name` | `string` | Not blank |
| `products[].category` | `string` | Not blank |
| `products[].price` | `number` | ≥ 0.00 |
| `products[].stock` | `integer` | ≥ 0 |

---

## curl Examples

### Download the sample report

```bash
curl -O -J http://localhost:8080/api/export/products/sample
```

> `-O` saves the file; `-J` uses the `Content-Disposition` filename.

### Generate a custom report (single product)

```bash
curl -X POST http://localhost:8080/api/export/products \
  -H "Content-Type: application/json" \
  -d '{
    "reportTitle": "My Custom Report",
    "products": [
      {
        "id": "PRD-001",
        "name": "Laptop Pro 15",
        "category": "Electronics",
        "price": 1299.99,
        "stock": 45
      }
    ]
  }' \
  -O -J
```

### Generate a multi-product report

```bash
curl -X POST http://localhost:8080/api/export/products \
  -H "Content-Type: application/json" \
  -d '{
    "reportTitle": "Full Catalogue Q1 2025",
    "products": [
      {"id":"PRD-001","name":"Laptop Pro 15",    "category":"Electronics","price":1299.99,"stock":45},
      {"id":"PRD-002","name":"Wireless Mouse",   "category":"Electronics","price":29.95,  "stock":200},
      {"id":"PRD-003","name":"Standing Desk",    "category":"Furniture",  "price":549.00, "stock":20},
      {"id":"PRD-004","name":"Java in Action",   "category":"Books",      "price":59.90,  "stock":80},
      {"id":"PRD-005","name":"Coffee Mug 350ml", "category":"Kitchen",    "price":14.99,  "stock":120}
    ]
  }' \
  -O -J
```

### Validation error example (blank title)

```bash
curl -X POST http://localhost:8080/api/export/products \
  -H "Content-Type: application/json" \
  -d '{"reportTitle":"","products":[{"id":"X","name":"Y","category":"Z","price":1.0,"stock":1}]}'
```

Expected response (HTTP 400):

```json
{
  "type": "https://example.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "reportTitle: Report title must not be blank"
}
```

---

## Running the tests

### Unit tests only

```bash
./mvnw test -Dtest=ExcelExportServiceTest
```

### Integration tests only

```bash
./mvnw test -Dtest=ExcelExportControllerIntegrationTest
```

### All tests

```bash
./mvnw clean test
```

---

## Test coverage

| Test class | Type | Description |
|---|---|---|
| `ExcelExportServiceTest` | **Unit** | Verifies workbook structure: title row, header row, data rows, totals row, cell types, formula types, row count |
| `ExcelExportControllerIntegrationTest` | **Integration** | Starts the full Spring Boot context on a random port; tests HTTP status codes, Content-Type, Content-Disposition, workbook content, and validation error responses |

Integration tests use **Testcontainers** (JUnit 5 extension) to manage the test lifecycle.
Since this application has no external dependencies (no database), no container is actually started —
Testcontainers is used here to demonstrate the standard integration-test pattern and to stay
consistent with the rest of the mini-project series.

---

## Excel workbook structure

| Row | Content |
|---|---|
| Row 0 | Merged title cell (dark blue background, white bold text) |
| Row 1 | Column headers: ID · Name · Category · Price (USD) · Stock |
| Rows 2…n | Product data rows (zebra-striped: white / light blue) |
| Last row | Totals: AVERAGE(price) and SUM(stock) Excel formulas |

Rows 0–1 are **frozen** so they remain visible while scrolling through large datasets.
Column widths are **auto-sized** with 20 % padding to prevent text truncation.
