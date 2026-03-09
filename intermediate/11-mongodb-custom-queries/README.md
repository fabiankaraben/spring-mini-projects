# MongoDB Custom Queries

A Spring Boot backend that demonstrates how to use **MongoTemplate** to build complex MongoDB **Aggregation Pipelines** — the powerful multi-stage query mechanism that goes far beyond what simple `MongoRepository` derived methods can express.

---

## What This Project Demonstrates

This mini-project uses a customer **order management domain** (orders with embedded line items) to showcase the following MongoDB aggregation concepts:

| Aggregation Concept | Where It's Used |
|---|---|
| `$group` + accumulators (`$sum`, `$avg`, `$max`, `$min`) | Revenue by region, customer spending summary |
| `$unwind` (explode embedded arrays) | Top products by revenue, category breakdown |
| `$match` before `$group` (early filtering) | Revenue by region/status, customer summary (exclude CANCELLED) |
| `$sort` + `$limit` | Top-N product ranking |
| `$project` (field renaming and reshaping) | All aggregations (rename `_id` to domain field names) |
| Date operators (`$year`, `$month`) | Monthly revenue time series |
| Compound `Criteria` queries | High-value orders in a time range |
| `MongoTemplate.find()` with `Criteria` | Orders above a minimum amount |

---

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included Maven Wrapper `./mvnw`)
- **Docker + Docker Compose** (required to run the application stack)
- Docker must be running locally to execute integration tests (Testcontainers)

---

## Project Structure

```
src/
├── main/java/com/example/mongodbcustomqueries/
│   ├── MongodbCustomQueriesApplication.java   # Spring Boot entry point
│   ├── config/
│   │   └── MongoConfig.java                   # BigDecimal ↔ Decimal128 converters
│   ├── controller/
│   │   └── OrderController.java               # REST endpoints
│   ├── domain/
│   │   ├── Order.java                         # @Document: top-level MongoDB document
│   │   └── OrderItem.java                     # Embedded sub-document (items array)
│   ├── dto/
│   │   ├── OrderRequest.java                  # Create order request DTO
│   │   ├── RevenueByRegionResult.java         # Aggregation result DTO
│   │   ├── TopProductResult.java              # Aggregation result DTO
│   │   └── CustomerSummaryResult.java         # Aggregation result DTO
│   ├── repository/
│   │   └── OrderRepository.java               # MongoRepository for basic CRUD
│   └── service/
│       └── OrderAggregationService.java       # MongoTemplate aggregation pipelines
└── test/java/com/example/mongodbcustomqueries/
    ├── OrderIntegrationTest.java              # Full integration tests (Testcontainers)
    ├── domain/
    │   └── OrderTest.java                     # Unit tests for domain model
    └── service/
        └── OrderAggregationServiceTest.java   # Unit tests for service (Mockito)
```

---

## Running with Docker Compose

The application requires MongoDB. The entire stack (app + MongoDB) can be started with Docker Compose:

```bash
# Build the application image and start all services
docker compose up --build

# Start in background
docker compose up --build -d

# Stop all services
docker compose down

# Stop and remove the data volume (start fresh)
docker compose down -v
```

The application will be available at **http://localhost:8080**.

MongoDB will be available at **mongodb://localhost:27017/ordersdb** and can be inspected with:

```bash
mongosh "mongodb://localhost:27017/ordersdb"
```

---

## API Endpoints

### CRUD

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/orders` | Create a new order |
| `GET` | `/api/orders` | List all orders |
| `GET` | `/api/orders/{id}` | Get order by ID |
| `DELETE` | `/api/orders/{id}` | Delete an order |

### Filters (MongoTemplate Criteria queries)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/orders/above-amount?min=500` | Orders with total ≥ min |
| `GET` | `/api/orders/high-value?start=...&end=...&min=...` | High-value orders in time range |

### Analytics (Aggregation Pipelines)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/orders/analytics/revenue-by-region` | Revenue grouped by region |
| `GET` | `/api/orders/analytics/revenue-by-region/{status}` | Revenue by region + status filter |
| `GET` | `/api/orders/analytics/top-products?topN=5` | Top-N products by revenue (`$unwind`) |
| `GET` | `/api/orders/analytics/customer-summary` | Customer spending summary (excludes CANCELLED) |
| `GET` | `/api/orders/analytics/status-distribution` | Count of orders per status |
| `GET` | `/api/orders/analytics/monthly-revenue` | Revenue grouped by year/month |
| `GET` | `/api/orders/analytics/category-breakdown` | Revenue per product category (`$unwind`) |

---

## curl Examples

### Create an order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Alice",
    "region": "North",
    "status": "DELIVERED",
    "totalAmount": 1129.97,
    "items": [
      { "productName": "Laptop",    "category": "electronics", "quantity": 1, "unitPrice": 999.99 },
      { "productName": "USB Cable", "category": "accessories", "quantity": 2, "unitPrice":  64.99 }
    ]
  }' | jq
```

### Create more orders for analytics

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Bob",
    "region": "South",
    "status": "SHIPPED",
    "totalAmount": 249.98,
    "items": [
      { "productName": "Headphones", "category": "electronics", "quantity": 2, "unitPrice": 124.99 }
    ]
  }' | jq

curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Alice",
    "region": "North",
    "status": "CANCELLED",
    "totalAmount": 499.99,
    "items": [
      { "productName": "Laptop", "category": "electronics", "quantity": 1, "unitPrice": 499.99 }
    ]
  }' | jq
```

### List all orders

```bash
curl -s http://localhost:8080/api/orders | jq
```

### Get order by ID

```bash
# Replace <id> with a real MongoDB ObjectId returned from POST
curl -s http://localhost:8080/api/orders/<id> | jq
```

### Orders above a minimum amount

```bash
curl -s "http://localhost:8080/api/orders/above-amount?min=500" | jq
```

### High-value orders in a time range

```bash
curl -s "http://localhost:8080/api/orders/high-value?start=2020-01-01T00:00:00Z&end=2030-12-31T23:59:59Z&min=500" | jq
```

### Revenue by region (aggregation)

```bash
curl -s http://localhost:8080/api/orders/analytics/revenue-by-region | jq
```

Expected output:
```json
[
  { "region": "North", "totalRevenue": 1129.97, "orderCount": 1, "avgOrderValue": 1129.97 },
  { "region": "South", "totalRevenue": 249.98,  "orderCount": 1, "avgOrderValue": 249.98  }
]
```

### Revenue by region for DELIVERED orders only

```bash
curl -s http://localhost:8080/api/orders/analytics/revenue-by-region/DELIVERED | jq
```

### Top 5 products by revenue (`$unwind` items)

```bash
curl -s "http://localhost:8080/api/orders/analytics/top-products?topN=5" | jq
```

Expected output:
```json
[
  { "productName": "Laptop",      "totalRevenue": 999.99, "totalQuantity": 1 },
  { "productName": "Headphones",  "totalRevenue": 249.98, "totalQuantity": 2 },
  { "productName": "USB Cable",   "totalRevenue": 129.98, "totalQuantity": 2 }
]
```

### Customer spending summary (excludes CANCELLED)

```bash
curl -s http://localhost:8080/api/orders/analytics/customer-summary | jq
```

Expected output — Alice's CANCELLED order is excluded:
```json
[
  { "customerName": "Alice", "totalSpent": 1129.97, "orderCount": 1, "maxOrder": 1129.97, "minOrder": 1129.97 },
  { "customerName": "Bob",   "totalSpent": 249.98,  "orderCount": 1, "maxOrder": 249.98,  "minOrder": 249.98  }
]
```

### Order status distribution

```bash
curl -s http://localhost:8080/api/orders/analytics/status-distribution | jq
```

### Monthly revenue time series

```bash
curl -s http://localhost:8080/api/orders/analytics/monthly-revenue | jq
```

### Revenue by product category (`$unwind` items)

```bash
curl -s http://localhost:8080/api/orders/analytics/category-breakdown | jq
```

### Delete an order

```bash
# Replace <id> with a real MongoDB ObjectId
curl -s -X DELETE http://localhost:8080/api/orders/<id> -w "%{http_code}"
# Expected: 204
```

---

## Running the Tests

Tests require **Docker to be running** (Testcontainers spins up a real MongoDB container).

```bash
# Run all tests (unit + integration)
./mvnw clean test

# Run only unit tests (no Docker required)
./mvnw clean test -Dtest="OrderTest,OrderAggregationServiceTest"

# Run only integration tests
./mvnw clean test -Dtest="OrderIntegrationTest"
```

### Test Categories

| Test Class | Type | Requires Docker |
|---|---|---|
| `OrderTest` | Unit — domain model behaviour | No |
| `OrderAggregationServiceTest` | Unit — service logic with Mockito mocks | No |
| `OrderIntegrationTest` | Integration — full stack with Testcontainers MongoDB | Yes |

### Integration test setup

Integration tests use [Testcontainers](https://testcontainers.com/) to spin up a **real `mongo:7.0` Docker container** automatically. No manual MongoDB setup is needed for tests — the container lifecycle is managed entirely by JUnit 5.

The `@DynamicPropertySource` annotation overrides `spring.data.mongodb.uri` at runtime to point Spring Boot at the randomly-assigned container port, ensuring tests are always isolated from any locally installed MongoDB instance.

---

## Key Concepts Reference

### Why MongoTemplate for Aggregations?

`MongoRepository` derived methods (e.g. `findByRegion(String)`) are powerful for simple queries but **cannot** express multi-stage aggregation pipelines. `MongoTemplate` provides the full aggregation API:

```java
// Example: revenue by region
Aggregation agg = Aggregation.newAggregation(
    Aggregation.group("region")
        .sum("totalAmount").as("totalRevenue")
        .count().as("orderCount"),
    Aggregation.sort(Sort.by(DESC, "totalRevenue"))
);
List<RevenueByRegionResult> results =
    mongoTemplate.aggregate(agg, "orders", RevenueByRegionResult.class)
                 .getMappedResults();
```

### The `$unwind` Stage

An order document contains an `items` **array**. To compute per-product statistics, `$unwind` "explodes" the array so each item becomes its own document:

```
Order { items: [Laptop, Mouse] }
  ↓ $unwind
{ items: { productName: "Laptop", ... } }
{ items: { productName: "Mouse",  ... } }
```

After unwinding, a `$group` on `items.productName` aggregates across all orders.

### BigDecimal → Decimal128 Conversion

`MongoConfig` registers custom converters that map Java `BigDecimal` ↔ BSON `Decimal128`. This is essential for correct numeric comparisons and arithmetic in aggregation expressions (`$sum`, `$avg`, `$multiply`). Without it, MongoDB stores decimals as strings and numeric operators produce wrong results.
