# 18 – GraphQL API

A Spring Boot backend that exposes a **GraphQL API** for a library domain (authors and books) using **Spring for GraphQL** and **PostgreSQL**.

## What this mini-project demonstrates

- Defining a GraphQL **schema** (types, queries, mutations, custom scalars) in a `.graphqls` file
- Wiring Java methods to GraphQL fields using `@QueryMapping` and `@MutationMapping`
- Binding GraphQL input arguments to Java DTOs with `@Argument`
- Registering a custom scalar (`Date` → `java.time.LocalDate`) via `graphql-java-extended-scalars`
- Persisting data with **Spring Data JPA** / **PostgreSQL**
- Testing GraphQL queries and mutations end-to-end with **Spring GraphQL Test** (`HttpGraphQlTester`)
- Full integration testing against a real PostgreSQL database using **Testcontainers**
- Running the complete stack (application + database) with **Docker Compose**

## Domain model

```
Author
  id            Long       (primary key)
  name          String     (required)
  bio           String     (optional)
  books         [Book]     (one-to-many)

Book
  id            Long       (primary key)
  title         String     (required)
  isbn          String     (required, unique)
  publishedDate Date       (optional, ISO-8601)
  genre         String     (optional)
  author        Author     (many-to-one, required)
```

## Requirements

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ (or use `./mvnw`) |
| Docker | 24+ with Docker Compose v2 |

## Running with Docker Compose

This is the recommended way to run the project. Docker Compose starts both PostgreSQL and the Spring Boot application.

```bash
# Build the image and start all services
docker compose up --build

# Run in the background
docker compose up --build -d

# View application logs
docker compose logs -f app

# Stop and remove containers (data volume is preserved)
docker compose down

# Stop and remove containers AND the data volume (clean slate)
docker compose down -v
```

The application will be available at **http://localhost:8080/graphql**.

The interactive **GraphiQL IDE** is available at **http://localhost:8080/graphiql** (browser).

## GraphQL Endpoint

All queries and mutations are sent as HTTP `POST` requests to `/graphql` with a JSON body:

```json
{
  "query": "...",
  "variables": {}
}
```

## curl Examples

### Query all authors

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ authors { id name bio books { title } } }"
  }'
```

### Query a single author by ID

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ author(id: 1) { id name bio } }"
  }'
```

### Search authors by name

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ searchAuthors(name: \"Orwell\") { id name } }"
  }'
```

### Create an author

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { createAuthor(input: { name: \"George Orwell\", bio: \"English novelist and essayist.\" }) { id name bio } }"
  }'
```

### Update an author

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { updateAuthor(id: 1, input: { name: \"George Orwell\", bio: \"Updated biography.\" }) { id name bio } }"
  }'
```

### Delete an author

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { deleteAuthor(id: 1) }"
  }'
```

### Query all books

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ books { id title isbn genre publishedDate author { name } } }"
  }'
```

### Create a book (requires an existing author ID)

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { createBook(input: { title: \"Nineteen Eighty-Four\", isbn: \"978-0-452-28423-4\", genre: \"Dystopian Fiction\", publishedDate: \"1949-06-08\", authorId: 1 }) { id title isbn author { name } } }"
  }'
```

### Filter books by genre

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ booksByGenre(genre: \"Dystopian Fiction\") { title isbn } }"
  }'
```

### Get all books by a specific author

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ booksByAuthor(authorId: 1) { title isbn genre } }"
  }'
```

### Search books by title

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ searchBooks(title: \"Eighty\") { id title } }"
  }'
```

### Nested query (books with author details)

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ books { title genre author { name bio } } }"
  }'
```

## Running tests

Tests require **Docker** to be running (Testcontainers pulls a PostgreSQL image).

```bash
./mvnw clean test
```

### Test categories

| Test class | Type | What it tests |
|---|---|---|
| `AuthorTest` | Unit | `Author` entity construction and field accessors |
| `BookTest` | Unit | `Book` entity construction and field accessors |
| `AuthorServiceTest` | Unit (Mockito) | `AuthorService` business logic with mocked repository |
| `BookServiceTest` | Unit (Mockito) | `BookService` business logic with mocked repositories |
| `GraphqlApiIntegrationTest` | Integration (Testcontainers) | Full end-to-end GraphQL queries/mutations against real PostgreSQL |

## Project structure

```
src/
├── main/
│   ├── java/com/example/graphqlapi/
│   │   ├── GraphqlApiApplication.java   # Spring Boot entry point
│   │   ├── config/
│   │   │   └── GraphQlConfig.java       # Registers custom Date scalar
│   │   ├── controller/
│   │   │   ├── AuthorController.java    # @QueryMapping / @MutationMapping for authors
│   │   │   └── BookController.java      # @QueryMapping / @MutationMapping for books
│   │   ├── domain/
│   │   │   ├── Author.java              # JPA entity
│   │   │   └── Book.java                # JPA entity
│   │   ├── dto/
│   │   │   ├── AuthorInput.java         # GraphQL input type for authors
│   │   │   └── BookInput.java           # GraphQL input type for books
│   │   ├── repository/
│   │   │   ├── AuthorRepository.java    # Spring Data JPA repository
│   │   │   └── BookRepository.java      # Spring Data JPA repository
│   │   └── service/
│   │       ├── AuthorService.java       # Business logic for authors
│   │       └── BookService.java         # Business logic for books
│   └── resources/
│       ├── graphql/
│       │   └── schema.graphqls          # GraphQL type definitions
│       └── application.yml              # Application configuration
└── test/
    ├── java/com/example/graphqlapi/
    │   ├── GraphqlApiIntegrationTest.java  # Testcontainers integration tests
    │   ├── domain/
    │   │   ├── AuthorTest.java             # Entity unit tests
    │   │   └── BookTest.java               # Entity unit tests
    │   └── service/
    │       ├── AuthorServiceTest.java      # Service unit tests (Mockito)
    │       └── BookServiceTest.java        # Service unit tests (Mockito)
    └── resources/
        ├── application-test.yml            # Test-specific configuration
        ├── docker-java.properties          # Docker API version fix for Testcontainers
        └── testcontainers.properties       # Testcontainers Docker API configuration
```
