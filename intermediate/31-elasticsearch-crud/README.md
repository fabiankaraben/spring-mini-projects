# Elasticsearch CRUD

A Spring Boot backend that indexes and searches documents using **Spring Data Elasticsearch**. This mini-project demonstrates how to:

- Index, retrieve, update, and delete documents in Elasticsearch via a REST API
- Perform full-text search across multiple fields using Elasticsearch's `multi_match` query
- Use Spring Data's **query derivation** (keyword-based exact-match and range queries)
- Write **unit tests** with JUnit 5 and Mockito (no Docker required)
- Write **integration tests** with Testcontainers (spins up a real Elasticsearch container)

---

## Requirements

| Tool | Version |
|------|---------|
| Java | 21 or higher |
| Maven | via included Maven Wrapper (`./mvnw`) |
| Docker | Required to run the full stack via Docker Compose |
| Docker Compose | V2 (`docker compose` command) |

---

## Project Structure

```
src/main/java/com/example/elasticsearchcrud/
├── ElasticsearchCrudApplication.java   # Spring Boot entry point
├── controller/
│   └── ArticleController.java          # REST endpoints
├── domain/
│   └── Article.java                    # Elasticsearch document entity
├── dto/
│   └── ArticleRequest.java             # Validated request DTO (record)
├── exception/
│   ├── ArticleNotFoundException.java   # Custom 404 exception
│   └── GlobalExceptionHandler.java     # Centralised error responses (RFC 9457)
├── repository/
│   └── ArticleRepository.java          # Spring Data ES repository (derived queries)
└── service/
    └── ArticleService.java             # Business logic + multi_match full-text search

src/test/java/com/example/elasticsearchcrud/
├── ArticleIntegrationTest.java         # Full-stack tests with Testcontainers
├── domain/
│   └── ArticleTest.java                # Domain entity unit tests
└── service/
    └── ArticleServiceTest.java         # Service unit tests with Mockito mocks
```

---

## API Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| `GET` | `/api/articles` | List all articles |
| `GET` | `/api/articles/{id}` | Get article by ID |
| `GET` | `/api/articles/search?q=<text>` | Full-text search (title + content) |
| `GET` | `/api/articles/author/{author}` | Filter by author (exact match) |
| `GET` | `/api/articles/category/{category}` | Filter by category (exact match) |
| `GET` | `/api/articles/popular?threshold=<n>` | Articles with views > threshold |
| `POST` | `/api/articles` | Create a new article |
| `PUT` | `/api/articles/{id}` | Update an existing article |
| `DELETE` | `/api/articles/{id}` | Delete an article |

### Article JSON schema

```json
{
  "title":     "string (required, max 255 chars)",
  "content":   "string (required)",
  "author":    "string (required)",
  "category":  "string (required)",
  "viewCount": "integer (required, >= 0)"
}
```

---

## Running with Docker Compose

This is the **recommended way** to run the project. Docker Compose starts Elasticsearch,
Kibana, and the Spring Boot application together.

### Start the full stack

```bash
docker compose up --build
```

- **Spring Boot API** → `http://localhost:8080`
- **Elasticsearch** → `http://localhost:9200`
- **Kibana** (UI to explore the index) → `http://localhost:5601`

### Stop and remove containers

```bash
docker compose down
```

### Remove containers **and** the Elasticsearch data volume (start fresh)

```bash
docker compose down -v
```

### Check Elasticsearch health directly

```bash
curl http://localhost:9200/_cluster/health
```

### Browse the `articles` index in Kibana

1. Open `http://localhost:5601`
2. Go to **Management → Stack Management → Index Management**
3. The `articles` index will appear after the first document is indexed.

---

## curl Examples

> All examples assume the app is running on `http://localhost:8080`.

### Create an article

```bash
curl -s -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Getting Started with Spring Boot",
    "content": "Spring Boot makes it easy to create stand-alone, production-grade applications.",
    "author": "Alice Johnson",
    "category": "technology",
    "viewCount": 0
  }' | jq .
```

### Create a second article

```bash
curl -s -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Docker for Developers",
    "content": "Docker containers allow you to package and ship applications consistently.",
    "author": "Bob Smith",
    "category": "devops",
    "viewCount": 250
  }' | jq .
```

### List all articles

```bash
curl -s http://localhost:8080/api/articles | jq .
```

### Get article by ID

```bash
# Replace <id> with the actual Elasticsearch-assigned document ID
curl -s http://localhost:8080/api/articles/<id> | jq .
```

### Full-text search (searches title AND content)

```bash
curl -s "http://localhost:8080/api/articles/search?q=spring+boot" | jq .
```

```bash
curl -s "http://localhost:8080/api/articles/search?q=containers" | jq .
```

### Filter by author (exact match)

```bash
curl -s http://localhost:8080/api/articles/author/Alice%20Johnson | jq .
```

### Filter by category

```bash
curl -s http://localhost:8080/api/articles/category/technology | jq .
```

### Get popular articles (views > 100)

```bash
curl -s "http://localhost:8080/api/articles/popular?threshold=100" | jq .
```

### Update an article

```bash
# Replace <id> with the actual document ID
curl -s -X PUT http://localhost:8080/api/articles/<id> \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Getting Started with Spring Boot 3",
    "content": "Spring Boot 3 requires Java 17+ and brings native image support.",
    "author": "Alice Johnson",
    "category": "technology",
    "viewCount": 42
  }' | jq .
```

### Delete an article

```bash
# Replace <id> with the actual document ID — returns HTTP 204 No Content
curl -s -X DELETE http://localhost:8080/api/articles/<id> -v
```

### Validation error example (blank title → HTTP 400)

```bash
curl -s -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{"title":"","content":"x","author":"x","category":"x","viewCount":0}' | jq .
```

### Not-found error example (HTTP 404)

```bash
curl -s http://localhost:8080/api/articles/nonexistent-id | jq .
```

---

## Running Tests

Tests require Docker to be running (Testcontainers pulls the Elasticsearch image automatically).

### Run all tests

```bash
./mvnw clean test
```

### Test categories

| Test class | Type | Description |
|------------|------|-------------|
| `ArticleTest` | Unit | Domain entity constructor and setter logic |
| `ArticleServiceTest` | Unit | Service business logic with Mockito mocks |
| `ArticleIntegrationTest` | Integration | Full-stack tests against a real Elasticsearch via Testcontainers |

---

## Key Concepts Demonstrated

### Spring Data Elasticsearch query derivation

The `ArticleRepository` extends `ElasticsearchRepository`. Method names are parsed by
Spring Data at startup and converted to Elasticsearch queries automatically:

```java
// Derived as: { "term": { "author": "<author>" } }
List<Article> findByAuthor(String author);

// Derived as: { "range": { "viewCount": { "gt": <threshold> } } }
List<Article> findByViewCountGreaterThan(int threshold);
```

### Multi-field full-text search

`ArticleService.fullTextSearch()` uses the **Elasticsearch Java API Client** directly to
build a `multi_match` query that simultaneously searches `title` and `content`:

```java
elasticsearchClient.search(s -> s
    .index("articles")
    .query(q -> q
        .multiMatch(mm -> mm
            .query(searchText)
            .fields("title", "content")
        )
    ),
Article.class);
```

### Field type mapping

| Java field | ES field type | Why |
|------------|--------------|-----|
| `title`, `content` | `text` | Analysed for full-text search (tokenised, stemmed) |
| `author`, `category` | `keyword` | Exact-match filtering, not tokenised |
| `viewCount` | `integer` | Numeric range queries |
| `createdAt`, `updatedAt` | `date` | ISO-8601 timestamp, range queries |
