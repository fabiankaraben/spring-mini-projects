# GraphQL Federation

A Spring Boot mini-project demonstrating **GraphQL Federation** — a pattern for composing multiple independent GraphQL services into a single unified API through a gateway.

## What This Project Demonstrates

GraphQL Federation solves a common microservices challenge: each service owns its own data, but clients want to query across all services in a single request.

### Architecture

```
Client
  │  POST /graphql  (single unified query)
  ▼
Gateway (port 8080)           ← Spring for GraphQL + WebClient
  ├──► products-service:8081  ← Owns the Product bounded-context
  └──► reviews-service:8082   ← Owns the Review bounded-context
```

The **gateway** exposes a unified "supergraph" schema. When a query arrives, it delegates each field to the appropriate downstream subgraph service over HTTP GraphQL, then merges the results into a single response.

### Key Concept: Federated Query

The `productWithReviews` query is the clearest example of federation value:

```graphql
query {
  productWithReviews(productId: "prod-1") {
    product {
      id
      name
      price
      category
    }
    reviews {
      author
      body
      rating
    }
  }
}
```

The client sends **one request to the gateway** and gets back data assembled from **two independent services** — without knowing those services exist.

### Module Structure

```
23-graphql-federation/
├── pom.xml                    # Parent POM (multi-module)
├── products-service/          # GraphQL subgraph — Product domain
│   ├── src/main/java/...
│   │   ├── model/Product.java
│   │   ├── repository/ProductRepository.java   # In-memory seeded data
│   │   └── resolver/ProductResolver.java       # @QueryMapping handlers
│   └── src/main/resources/
│       ├── graphql/schema.graphqls
│       └── application.yml    # port: 8081
├── reviews-service/           # GraphQL subgraph — Review domain
│   ├── src/main/java/...
│   │   ├── model/Review.java
│   │   ├── repository/ReviewRepository.java    # In-memory seeded data
│   │   └── resolver/ReviewResolver.java        # @QueryMapping handlers
│   └── src/main/resources/
│       ├── graphql/schema.graphqls
│       └── application.yml    # port: 8082
└── gateway/                   # Federation gateway — unified supergraph
    ├── src/main/java/...
    │   ├── client/ProductsClient.java    # WebClient → products-service
    │   ├── client/ReviewsClient.java     # WebClient → reviews-service
    │   ├── model/Product.java            # DTO mirroring subgraph type
    │   ├── model/Review.java             # DTO mirroring subgraph type
    │   ├── model/ProductWithReviews.java # Gateway-native composite type
    │   └── resolver/GatewayQueryResolver.java  # Stitches both services
    └── src/main/resources/
        ├── graphql/schema.graphqls       # Unified supergraph schema
        └── application.yml              # port: 8080
```

## Requirements

- **Java 21+**
- **Maven 3.9+** (or use the included `./mvnw` wrapper)
- **Docker** and **Docker Compose** (for running the full stack)

## Running with Docker Compose

This is the recommended way to run the entire federation stack:

```bash
# Build all three service images and start the stack
docker compose up --build

# Or run in the background
docker compose up --build -d

# View logs
docker compose logs -f

# Stop everything
docker compose down
```

Once all three services are healthy, the gateway is available at **http://localhost:8080**.

Browser-based GraphQL IDE (GraphiQL) is available at **http://localhost:8080/graphiql**.

The individual subgraph services are also exposed directly (for debugging):
- Products subgraph: http://localhost:8081/graphiql
- Reviews subgraph: http://localhost:8082/graphiql

## Usage Examples (curl)

All queries go to the gateway at port **8080**.

### List all products

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ products { id name price category inStock } }"}' | jq .
```

### Get a single product by ID

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ product(id: \"prod-1\") { id name description price } }"}' | jq .
```

### Filter products by category

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ productsByCategory(category: \"Electronics\") { id name price } }"}' | jq .
```

### List only in-stock products

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ productsInStock { id name price } }"}' | jq .
```

### List all reviews

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ reviews { id productId author body rating } }"}' | jq .
```

### Get reviews for a specific product

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ reviewsByProduct(productId: \"prod-1\") { id author body rating } }"}' | jq .
```

### Filter reviews by minimum rating

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ reviewsByMinRating(minRating: 4) { id author rating } }"}' | jq .
```

### THE federation query — product with its reviews (cross-service)

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ productWithReviews(productId: \"prod-1\") { product { id name price category } reviews { id author body rating } } }"
  }' | jq .
```

Expected response (data assembled from two services):
```json
{
  "data": {
    "productWithReviews": {
      "product": {
        "id": "prod-1",
        "name": "Wireless Keyboard",
        "price": 79.99,
        "category": "Electronics"
      },
      "reviews": [
        { "id": "rev-1", "author": "Alice", "body": "Great keyboard!", "rating": 5 },
        { "id": "rev-2", "author": "Bob", "body": "Battery life is short.", "rating": 4 }
      ]
    }
  }
}
```

### All products with their reviews

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ allProductsWithReviews { product { id name category } reviews { author rating } } }"
  }' | jq .
```

## Running Tests

Tests do **not** require Docker — they use in-memory repositories and embedded WireMock.

```bash
# Run all tests across all modules
./mvnw clean test

# Run tests for a specific module only
./mvnw clean test -pl products-service
./mvnw clean test -pl reviews-service
./mvnw clean test -pl gateway
```

### Test Structure

| Module | Test Class | Type | Description |
|---|---|---|---|
| `products-service` | `ProductRepositoryTest` | Unit | In-memory repository query logic |
| `products-service` | `ProductResolverIntegrationTest` | Integration | GraphQL endpoint via MockMvc |
| `reviews-service` | `ReviewRepositoryTest` | Unit | In-memory repository query logic |
| `reviews-service` | `ReviewResolverIntegrationTest` | Integration | GraphQL endpoint via MockMvc |
| `gateway` | `GatewayQueryResolverTest` | Unit | Resolver orchestration with Mockito mocks |
| `gateway` | `GatewayIntegrationTest` | Integration | Full gateway stack with embedded WireMock |

### Test Technologies

- **JUnit 5** — test framework
- **AssertJ** — fluent assertions
- **Mockito** — mock dependencies in unit tests (`GatewayQueryResolverTest`)
- **Spring for GraphQL Test** — `GraphQlTester` for testing GraphQL endpoints
- **WireMock** — embedded HTTP stub server simulating the subgraph services in gateway integration tests (no Docker required)

## Seeded Data

Both services start with in-memory data for immediate use.

### Products (8 items)

| ID | Name | Category | Price | In Stock |
|---|---|---|---|---|
| prod-1 | Wireless Keyboard | Electronics | $79.99 | Yes |
| prod-2 | Ergonomic Mouse | Electronics | $49.99 | Yes |
| prod-3 | Standing Desk | Furniture | $349.99 | Yes |
| prod-4 | Office Chair | Furniture | $249.99 | **No** |
| prod-5 | USB-C Hub | Electronics | $39.99 | Yes |
| prod-6 | 4K Monitor | Electronics | $599.99 | Yes |
| prod-7 | Laptop Stand | Electronics | $29.99 | **No** |
| prod-8 | Bookshelf | Furniture | $149.99 | Yes |

### Reviews (10 items across products 1–3, 5–6)

Products 4, 7, and 8 have no reviews, which can be verified with `reviewsByProduct`.
