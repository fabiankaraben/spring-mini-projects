# 33 · Basic Caching

A Spring Boot mini-project demonstrating how to use Spring's **caching abstraction** with `@Cacheable`, `@CachePut`, and `@CacheEvict` backed by a simple in-memory **`ConcurrentHashMap`** (via `ConcurrentMapCacheManager`).

---

## What This Project Demonstrates

| Concept | Description |
|---|---|
| `@EnableCaching` | Activates Spring's annotation-driven cache management |
| `@Cacheable` | Caches the return value of a method; skips the method body on subsequent calls with the same key |
| `@CachePut` | Always executes the method and updates the cache entry with the new result |
| `@CacheEvict` | Removes one or all entries from a named cache |
| `ConcurrentMapCacheManager` | Default in-memory cache manager backed by `ConcurrentHashMap` — no external dependency needed |

The service simulates a slow data source (500 ms delay) so the caching speedup is clearly observable: the **first call** takes ~500 ms, all **subsequent calls** with the same key return instantly from the cache.

---

## Requirements

| Tool | Version |
|---|---|
| Java (JDK) | 21 or later |
| Maven | 3.9.6 (included via Maven Wrapper — no local install needed) |

No database or Docker is required. All data is stored in an in-memory `ConcurrentHashMap` inside the service.

---

## Project Structure

```
33-basic-caching/
├── src/
│   ├── main/
│   │   ├── java/com/example/basiccaching/
│   │   │   ├── BasicCachingApplication.java   # Entry point with @EnableCaching
│   │   │   ├── config/
│   │   │   │   └── CacheConfig.java           # Explicit ConcurrentMapCacheManager bean
│   │   │   ├── controller/
│   │   │   │   └── ProductController.java     # REST endpoints (CRUD)
│   │   │   ├── model/
│   │   │   │   └── Product.java               # Java record (immutable data carrier)
│   │   │   └── service/
│   │   │       └── ProductService.java        # @Cacheable / @CachePut / @CacheEvict
│   │   └── resources/
│   │       └── application.yml               # Spring Cache type: simple
│   └── test/
│       └── java/com/example/basiccaching/
│           ├── BasicCachingApplicationTests.java              # Context load smoke test
│           ├── controller/
│           │   └── ProductControllerTest.java                 # @WebMvcTest sliced tests
│           └── service/
│               ├── ProductServiceIntegrationTest.java         # Cache integration tests
│               └── ProductServiceUnitTest.java                # Pure unit tests
├── .gitignore
├── mvnw / mvnw.cmd                            # Maven Wrapper scripts
├── pom.xml
└── README.md
```

---

## How to Run

### Using Maven Wrapper (recommended)

```bash
# From the project root directory:
./mvnw spring-boot:run
```

The application starts on **http://localhost:8080**.

---

## API Endpoints & `curl` Examples

Four pre-seeded products are loaded at startup (Wireless Keyboard, Ergonomic Mouse, Standing Desk, Notebook).

### List all products

```bash
curl -s http://localhost:8080/api/products | jq
```

> The **first request** takes ~500 ms (real call + cache miss). Call it again immediately — it returns instantly from the cache.

### Get a product by ID

```bash
curl -s http://localhost:8080/api/products/1 | jq
```

Returns `404 Not Found` if the ID does not exist.

### Create a new product

```bash
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": "Mechanical Pencil", "category": "Stationery", "price": 8.49}' | jq
```

Returns `201 Created` with the saved product including its assigned ID.  
This also **evicts** the `"all"` list from the cache so the next `GET /api/products` reflects the addition.

### Update an existing product

```bash
curl -s -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "Wireless Keyboard Pro", "category": "Electronics", "price": 79.99}' | jq
```

Returns `200 OK` with the updated product. The cache entry for ID 1 is also updated via `@CachePut`.

### Delete a product

```bash
curl -s -X DELETE http://localhost:8080/api/products/2 -w "\nHTTP Status: %{http_code}\n"
```

Returns `204 No Content` on success, or `404 Not Found` if the ID does not exist.  
The cache entry for that ID is evicted via `@CacheEvict`.

---

## Observing Caching in Action

Watch the application logs while making requests. You will see:

```
>>> Executing REAL (non-cached) call: findAll
<<< Finished real call: findAll
```

This log line appears **only on a cache miss**. On a cache hit the method body is skipped entirely — no log line, no 500 ms delay.

**Example workflow to see the difference:**

```bash
# First call — slow (~500ms), REAL call logged
curl http://localhost:8080/api/products

# Second call — instant, NO log line (served from cache)
curl http://localhost:8080/api/products

# Add a product — evicts the "all" cache entry
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"New Item","category":"Misc","price":1.00}'

# Third call — slow again (~500ms), cache was evicted by the POST
curl http://localhost:8080/api/products
```

---

## Running the Tests

```bash
./mvnw test
```

### Test Coverage

| Test Class | Type | What it tests |
|---|---|---|
| `BasicCachingApplicationTests` | Spring Boot (full context) | Application context loads without errors |
| `ProductControllerTest` | `@WebMvcTest` (sliced) | HTTP routing, status codes, JSON serialization |
| `ProductServiceIntegrationTest` | `@SpringBootTest` (full context) | `@Cacheable`, `@CachePut`, `@CacheEvict` with real `CacheManager` |
| `ProductServiceUnitTest` | Pure unit test (no Spring) | Business logic: save, find, delete in the in-memory map |

> **Note on unit vs integration cache tests:** Spring's caching annotations work via **AOP proxies**. When instantiating `ProductService` with `new` (unit test), no proxy is applied and caching is bypassed. Cache behavior is therefore verified only in `ProductServiceIntegrationTest` where the full Spring context is active and AOP is enabled.

---

## Key Concepts Explained

### Why `ConcurrentHashMap`?

`ConcurrentMapCacheManager` is Spring's **simplest cache implementation**. It uses a `ConcurrentHashMap` per named cache region. Advantages:

- ✅ Zero configuration — works out of the box
- ✅ Thread-safe
- ✅ No external dependencies

Limitations:
- ❌ Not distributed — each JVM instance has its own cache
- ❌ No TTL (entries never expire)
- ❌ Data lost on restart

For production use, consider **Redis** (`spring-boot-starter-data-redis`) or **Caffeine** (`spring-boot-starter-cache` + Caffeine on the classpath).

### Cache Key Strategy

| Method | Cache Name | Cache Key |
|---|---|---|
| `findAll()` | `products` | `"all"` (literal string) |
| `findById(id)` | `products` | `id` value |
| `save(product)` | `products` | `result.id()` (returned product's ID) |
| `deleteById(id)` | `products` | `id` value (evicted) |
