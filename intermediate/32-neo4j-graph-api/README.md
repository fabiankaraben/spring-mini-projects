# Neo4j Graph API

A Spring Boot REST API that models and queries nodes and relationships using **Spring Data Neo4j**. This project demonstrates graph database concepts — nodes, relationships, and Cypher traversal queries — through a movie/person social-graph domain.

## Graph Model

```
(Person)-[:ACTED_IN]->(Movie)
(Person)-[:DIRECTED]->(Movie)
(Person)-[:FOLLOWS]->(Person)
```

**Nodes:**
- `Person` — name, born (year)
- `Movie` — title, released (year), tagline

**Relationships:**
- `ACTED_IN` — directed edge from Person to Movie
- `DIRECTED` — directed edge from Person to Movie
- `FOLLOWS` — directed social edge from Person to Person

On startup, the app seeds the graph with sample data (Keanu Reeves, Laurence Fishburne, The Matrix, John Wick, etc.) if the database is empty.

## Requirements

- **Java 21+**
- **Maven** (or use the included Maven Wrapper `./mvnw`)
- **Docker** and **Docker Compose** (for running Neo4j and the full stack)
- Docker must be running when executing integration tests (Testcontainers pulls `neo4j:5`)

## Running with Docker Compose

The entire stack (Neo4j + Spring Boot app) runs via Docker Compose:

```bash
# Build and start all services
docker compose up --build

# Run in background
docker compose up --build -d

# Stop all services
docker compose down

# Stop and remove persisted volumes (wipes graph data)
docker compose down -v
```

Once running:
- **REST API**: http://localhost:8080
- **Neo4j Browser** (visual graph explorer): http://localhost:7474
  - Connect with: `bolt://localhost:7687`, username `neo4j`, password `password`

## Running Locally (without Docker for the app)

You still need Neo4j running. Start only the database:

```bash
docker compose up neo4j -d
```

Then run the app directly:

```bash
./mvnw spring-boot:run
```

## API Endpoints

### Movies

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/movies` | Create a movie node |
| GET | `/api/movies` | List all movies |
| GET | `/api/movies/{id}` | Get movie by ID |
| GET | `/api/movies/search?title=...` | Find movie by title |
| GET | `/api/movies/year/{year}` | Movies released in a year |
| GET | `/api/movies/year-range?minYear=...&maxYear=...` | Movies in year range |
| DELETE | `/api/movies/{id}` | Delete a movie node |
| GET | `/api/movies/acted-in-by/{personName}` | Graph traversal: movies an actor was in |
| GET | `/api/movies/directed-by/{personName}` | Graph traversal: movies a director made |
| GET | `/api/movies/recommendations/{personName}` | Co-actor movie recommendations |

### Persons

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/persons` | Create a person node |
| GET | `/api/persons` | List all persons |
| GET | `/api/persons/{id}` | Get person by ID |
| GET | `/api/persons/search?name=...` | Find person by name |
| GET | `/api/persons/born?minYear=...&maxYear=...` | Persons by birth year range |
| DELETE | `/api/persons/{id}` | Delete a person node |
| POST | `/api/persons/{person}/acted-in/{movie}` | Create ACTED_IN relationship |
| POST | `/api/persons/{person}/directed/{movie}` | Create DIRECTED relationship |
| POST | `/api/persons/{follower}/follows/{followed}` | Create FOLLOWS relationship |
| GET | `/api/persons/actors-in/{movieTitle}` | Graph traversal: actors in a movie |
| GET | `/api/persons/directors-of/{movieTitle}` | Graph traversal: directors of a movie |
| GET | `/api/persons/{personName}/following` | Graph traversal: who does this person follow |

## curl Examples

### Create a Movie

```bash
curl -s -X POST http://localhost:8080/api/movies \
  -H "Content-Type: application/json" \
  -d '{"title": "Speed", "released": 1994, "tagline": "Get ready for rush hour."}'
```

### Create a Person

```bash
curl -s -X POST http://localhost:8080/api/persons \
  -H "Content-Type: application/json" \
  -d '{"name": "Sandra Bullock", "born": 1964}'
```

### Create an ACTED_IN Relationship

```bash
# After seeding, Keanu Reeves and The Matrix already exist
curl -s -X POST "http://localhost:8080/api/persons/Keanu%20Reeves/acted-in/The%20Matrix"
```

### Create a DIRECTED Relationship

```bash
curl -s -X POST "http://localhost:8080/api/persons/Lana%20Wachowski/directed/The%20Matrix"
```

### Create a FOLLOWS Relationship (social graph)

```bash
curl -s -X POST "http://localhost:8080/api/persons/Keanu%20Reeves/follows/Laurence%20Fishburne"
```

### Graph Traversal — Who Acted in The Matrix?

```bash
curl -s "http://localhost:8080/api/persons/actors-in/The%20Matrix"
```

### Graph Traversal — What Movies Has Keanu Reeves Been In?

```bash
curl -s "http://localhost:8080/api/movies/acted-in-by/Keanu%20Reeves"
```

### Movie Recommendations (2-hop Co-actor Traversal)

```bash
# Returns movies Keanu's co-actors have been in, that Keanu has not
curl -s "http://localhost:8080/api/movies/recommendations/Keanu%20Reeves"
```

### Find Persons Born in a Range

```bash
curl -s "http://localhost:8080/api/persons/born?minYear=1960&maxYear=1970"
```

### List All Movies

```bash
curl -s http://localhost:8080/api/movies | python3 -m json.tool
```

### Delete a Movie by ID

```bash
# First get the ID
curl -s "http://localhost:8080/api/movies/search?title=Speed" | python3 -m json.tool
# Then delete
curl -s -X DELETE http://localhost:8080/api/movies/42
```

## Running the Tests

Tests require Docker to be running (Testcontainers pulls `neo4j:5` automatically).

```bash
./mvnw clean test
```

### Test Structure

```
src/test/java/com/example/neo4jgraphapi/
├── domain/
│   ├── PersonTest.java          # Unit: Person entity logic
│   └── MovieTest.java           # Unit: Movie entity logic
├── service/
│   ├── PersonServiceTest.java   # Unit: PersonService with Mockito
│   └── MovieServiceTest.java    # Unit: MovieService with Mockito
└── integration/
    └── Neo4jIntegrationTest.java  # Full integration: Testcontainers + MockMvc
```

**Unit tests** (`domain/`, `service/`) use JUnit 5 + Mockito. No Spring context, no database — fast.

**Integration tests** (`integration/`) use:
- `@Testcontainers` + `@Container` — spins up a real `neo4j:5` Docker container
- `@DynamicPropertySource` — injects the container's bolt URL into the Spring context
- `@SpringBootTest` + `@AutoConfigureMockMvc` — full HTTP stack testing
- `@BeforeEach deleteAll()` — clean database state between tests

## Project Structure

```
src/main/java/com/example/neo4jgraphapi/
├── Neo4jGraphApiApplication.java    # Spring Boot entry point
├── config/
│   └── DataInitializer.java         # Seeds sample graph data on startup
├── domain/
│   ├── Person.java                  # @Node: Person with relationship lists
│   └── Movie.java                   # @Node: Movie
├── dto/
│   ├── CreatePersonRequest.java     # Request DTO for POST /api/persons
│   └── CreateMovieRequest.java      # Request DTO for POST /api/movies
├── repository/
│   ├── PersonRepository.java        # Neo4jRepository + custom @Query methods
│   └── MovieRepository.java         # Neo4jRepository + custom @Query methods
├── service/
│   ├── PersonService.java           # Business logic for Person operations
│   └── MovieService.java            # Business logic for Movie operations
└── controller/
    ├── PersonController.java        # REST endpoints for /api/persons
    └── MovieController.java         # REST endpoints for /api/movies
```

## Key Concepts Demonstrated

- **`@Node`** — marks a Java class as a Neo4j graph node (vertex)
- **`@Relationship`** — declares a typed, directed graph edge between nodes
- **`@Id @GeneratedValue`** — Neo4j-assigned internal node ID
- **`Neo4jRepository`** — Spring Data repository with built-in CRUD + Cypher-derived queries
- **`@Query`** — explicit Cypher queries for multi-hop graph traversals
- **`@DynamicPropertySource`** — Testcontainers integration with Spring context
- **Graph traversal patterns**: one-hop (actor → movie), two-hop (co-actor recommendations)
