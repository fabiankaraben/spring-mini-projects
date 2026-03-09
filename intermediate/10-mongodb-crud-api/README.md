# MongoDB CRUD API

A Spring Boot backend that demonstrates basic **NoSQL CRUD operations** using **Spring Data MongoDB**. The application exposes a RESTful API for managing a product catalogue, with documents stored in a MongoDB collection.

---

## What This Mini-Project Demonstrates

- **Spring Data MongoDB** – repository pattern, derived query methods, `@Query` annotation
- **Document modelling** – `@Document`, `@Id`, `@Field`, `@Indexed`
- **Full CRUD via REST** – `GET`, `POST`, `PUT`, `DELETE` endpoints
- **Extra query endpoints** – search by name, filter by category, filter by max price, low-stock alert
- **Bean Validation** – `@NotBlank`, `@DecimalMin`, `@Min` on request DTOs
- **DTO pattern** – separating the API contract (`ProductRequest`) from the domain model (`Product`)
- **Unit tests** – domain model and service logic tested in isolation with JUnit 5 + Mockito
- **Integration tests** – full-stack tests with a real MongoDB container via Testcontainers
- **Docker Compose** – runs MongoDB + the Spring Boot app as a self-contained stack

---

## Requirements

| Tool | Minimum version |
|------|----------------|
| Java | 21 |
| Maven | 3.9+ (or use included Maven Wrapper `./mvnw`) |
| Docker | 24+ (required to run the app and integration tests) |
| Docker Compose | v2 (`docker compose` command) |

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/mongodbcrudapi/
│   │   ├── MongodbCrudApiApplication.java   # Spring Boot entry point
│   │   ├── controller/
│   │   │   └── ProductController.java       # REST endpoints
│   │   ├── domain/
│   │   │   └── Product.java                 # MongoDB document model
│   │   ├── dto/
│   │   │   └── ProductRequest.java          # Request DTO with validation
│   │   ├── repository/
│   │   │   └── ProductRepository.java       # Spring Data MongoDB repository
│   │   └── service/
│   │       └── ProductService.java          # Business logic layer
│   └── resources/
│       └── application.yml                  # App configuration
└── test/
    ├── java/com/example/mongodbcrudapi/
    │   ├── ProductIntegrationTest.java       # Full-stack integration tests
    │   ├── domain/
    │   │   └── ProductTest.java              # Domain model unit tests
    │   └── service/
    │       └── ProductServiceTest.java       # Service unit tests (Mockito)
    └── resources/
        ├── application-test.yml             # Test profile config
        ├── docker-java.properties           # Docker API version fix for Testcontainers
        └── testcontainers.properties        # Testcontainers Docker API config
```

---

## Running with Docker Compose

This is the recommended way to run the project. Docker Compose starts both the MongoDB database and the Spring Boot application.

### 1. Build and start all services

```bash
docker compose up --build
```

> The first run downloads the `mongo:7.0` and `eclipse-temurin:21-jdk-alpine` images and builds the application JAR. Subsequent runs are much faster.

### 2. Stop and remove containers

```bash
docker compose down
```

### 3. Stop and also remove the persistent MongoDB data volume

```bash
docker compose down -v
```

### 4. View application logs

```bash
docker compose logs -f app
```

Once running, the API is available at `http://localhost:8080`.

---

## Running Locally (without Docker)

If you have MongoDB running locally on the default port (`localhost:27017`):

```bash
./mvnw spring-boot:run
```

Or with a custom MongoDB host/port:

```bash
MONGODB_HOST=myserver MONGODB_PORT=27017 ./mvnw spring-boot:run
```

---

## API Endpoints & curl Examples

All endpoints are prefixed with `/api/products`.

### List all products

```bash
curl -s http://localhost:8080/api/products | jq
```

### Get a product by ID

```bash
curl -s http://localhost:8080/api/products/507f1f77bcf86cd799439011 | jq
```

### Search products by name (case-insensitive substring)

```bash
curl -s "http://localhost:8080/api/products/search?name=laptop" | jq
```

### Filter products by category

```bash
curl -s http://localhost:8080/api/products/category/electronics | jq
```

### Filter products by maximum price

```bash
curl -s "http://localhost:8080/api/products/max-price?price=50.00" | jq
```

### Get low-stock products (default threshold = 10 units)

```bash
curl -s "http://localhost:8080/api/products/low-stock?threshold=5" | jq
```

### Create a new product

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mechanical Keyboard",
    "description": "Tactile switches, RGB backlight",
    "price": 89.99,
    "category": "peripherals",
    "stockQuantity": 75
  }' | jq
```

**Response (201 Created):**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "name": "Mechanical Keyboard",
  "description": "Tactile switches, RGB backlight",
  "price": 89.99,
  "category": "peripherals",
  "stockQuantity": 75,
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

### Update a product (full replacement)

```bash
curl -s -X PUT http://localhost:8080/api/products/507f1f77bcf86cd799439011 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mechanical Keyboard v2",
    "description": "Updated model with wrist rest",
    "price": 109.99,
    "category": "peripherals",
    "stockQuantity": 60
  }' | jq
```

### Delete a product

```bash
curl -s -X DELETE http://localhost:8080/api/products/507f1f77bcf86cd799439011 -v
```

**Response: HTTP 204 No Content**

### Validation error example (400 Bad Request)

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": "", "description": "test", "price": -1, "category": "", "stockQuantity": -5}' | jq
```

---

## HTTP Status Codes

| Method | Endpoint | Success | Not Found | Invalid Input |
|--------|----------|---------|-----------|---------------|
| `GET` | `/api/products` | 200 OK | — | — |
| `GET` | `/api/products/{id}` | 200 OK | 404 Not Found | — |
| `GET` | `/api/products/search` | 200 OK | — | — |
| `GET` | `/api/products/category/{cat}` | 200 OK | — | — |
| `GET` | `/api/products/max-price` | 200 OK | — | — |
| `GET` | `/api/products/low-stock` | 200 OK | — | — |
| `POST` | `/api/products` | 201 Created | — | 400 Bad Request |
| `PUT` | `/api/products/{id}` | 200 OK | 404 Not Found | 400 Bad Request |
| `DELETE` | `/api/products/{id}` | 204 No Content | 404 Not Found | — |

---

## Connecting to MongoDB directly

While the stack is running, you can connect to MongoDB using `mongosh` inside the container:

```bash
docker exec -it mongodbcrudapi-mongodb mongosh productdb
```

Useful MongoDB shell commands:

```js
// List all products
db.products.find().pretty()

// Count documents
db.products.countDocuments()

// Find by category
db.products.find({ category: "electronics" })

// Find low-stock items
db.products.find({ stock_quantity: { $lt: 10 } })
```

---

## Running Tests

Integration tests require Docker to be running (Testcontainers pulls the `mongo:7.0` image automatically).

### Run all tests (unit + integration)

```bash
./mvnw clean test
```

### Test summary

| Test class | Type | Description |
|---|---|---|
| `ProductTest` | Unit | Domain model constructors, setters, toString, BigDecimal precision |
| `ProductServiceTest` | Unit | Service logic with mocked repository (no MongoDB needed) |
| `ProductIntegrationTest` | Integration | Full-stack HTTP tests with real MongoDB via Testcontainers |

### What the integration tests verify

- All CRUD HTTP endpoints return correct status codes
- Search by name (case-insensitive), filter by category, max-price, low-stock
- Bean Validation rejects blank names, zero/negative prices, negative stock
- Created documents are persisted and retrievable from MongoDB
- Updated documents reflect new values in MongoDB
- Deleted documents are removed from MongoDB
- The `findByCategory` derived query returns only matching documents

---

## MongoDB Data Model

Each product document in the `products` collection has the following shape:

```json
{
  "_id": "ObjectId('507f1f77bcf86cd799439011')",
  "name": "Laptop Pro",
  "description": "High-performance laptop",
  "price": "Decimal128('1299.99')",
  "category": "electronics",
  "stock_quantity": 50,
  "created_at": "ISODate('2025-01-15T10:30:00Z')",
  "updated_at": "ISODate('2025-01-15T10:30:00Z')"
}
```

**Indexes** (created automatically by `@Indexed` on the domain model):
- `name` – single-field index for name-based lookups
- `category` – single-field index for category filtering

---

## Key Concepts Explained

### Why `String` for the MongoDB `@Id`?

MongoDB uses BSON `ObjectId` internally, but Spring Data MongoDB automatically converts it to a hex `String` when the `@Id` field is declared as `String`. This keeps the JSON API clean — clients receive a plain string ID rather than a complex `{"$oid": "..."}` wrapper.

### Derived Query Methods

Spring Data MongoDB reads method names like `findByCategory(String)` and automatically generates the MongoDB query `{ "category": "<value>" }` at startup — no implementation code needed. This dramatically reduces boilerplate.

### `@Query` Annotation

For queries that can't be expressed cleanly with method naming, the `@Query` annotation accepts a MongoDB JSON query string directly. Parameter placeholders (`?0`, `?1`, ...) are replaced with method arguments at runtime.

### BigDecimal for Prices

Using `double` for monetary values causes floating-point rounding errors (e.g. `0.1 + 0.2 ≠ 0.3`). `BigDecimal` stores exact decimal values and Spring Data MongoDB converts it to the `Decimal128` BSON type, which preserves full precision.
