# GraphQL Mutations — Spring Boot Mini-Project

A Spring Boot backend demonstrating how to handle **state changes through GraphQL mutations**. The domain is a **Task Manager** with Projects and Tasks, showcasing two key mutation patterns:

1. **Standard CRUD mutations** — create, update, and delete resources.
2. **State-transition mutations** — intent-revealing operations that encode business rules (`startTask`, `completeTask`, `reopenTask`), a key GraphQL best practice over generic field-update mutations.

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | via Maven Wrapper (included) |
| Docker | Required (PostgreSQL runs in Docker) |
| Docker Compose | Included with Docker Desktop |

## Architecture

```
Client (curl / GraphiQL)
        │
        ▼  HTTP POST /graphql
Spring for GraphQL (schema.graphqls)
        │
        ▼
GraphQL Controller (@QueryMapping / @MutationMapping)
        │
        ▼
Service Layer (business rules + state-transition logic)
        │
        ▼
Spring Data JPA Repository
        │
        ▼
PostgreSQL (Docker)
```

### Domain Model

```
Project ──< Task
  - id              - id
  - name            - title
  - description     - description
  - tasks[]         - status: TODO | IN_PROGRESS | DONE
                    - priority: 1–5
                    - project (FK)
```

### Task Status Lifecycle

```
  ┌─────────────────────────────────┐
  │                                 │
  ▼          startTask              │ reopenTask
 TODO ──────────────────► IN_PROGRESS
  ▲                           │
  │       completeTask        │ completeTask
  │  ◄────────────────────────┘
  │
  └────────────────── DONE ◄────────
```

## Running with Docker Compose

The entire application (Spring Boot + PostgreSQL) runs via Docker Compose.

### Start the application

```bash
docker compose up --build
```

This will:
1. Build the Spring Boot application image.
2. Start a PostgreSQL 16 container.
3. Wait for PostgreSQL to be healthy, then start the application.
4. The app is available at `http://localhost:8080`.

### Stop the application

```bash
docker compose down
```

### Wipe data and start fresh

```bash
docker compose down -v
docker compose up --build
```

## GraphiQL IDE

With the application running, open **http://localhost:8080/graphiql** in your browser for an interactive GraphQL IDE where you can explore the schema and run queries/mutations.

## API Usage — curl Examples

All GraphQL requests are `POST` to `http://localhost:8080/graphql`.

---

### Project Mutations

#### Create a project

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { createProject(input: { name: \"Backend Refactor\", description: \"Refactor all legacy services\" }) { id name description } }"
  }'
```

#### Update a project

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { updateProject(id: 1, input: { name: \"Backend Refactor v2\", description: \"Updated plan\" }) { id name description } }"
  }'
```

#### Delete a project (also deletes all its tasks)

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { deleteProject(id: 1) }"
  }'
```

---

### Task Mutations

#### Create a task (always starts with TODO status)

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { createTask(input: { title: \"Fix login bug\", description: \"Users cannot log in on mobile\", priority: 1, projectId: 1 }) { id title status priority project { name } } }"
  }'
```

#### Update a task (title, description, priority — NOT status)

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { updateTask(id: 1, input: { title: \"Fix login bug (critical)\", priority: 1, projectId: 1 }) { id title priority } }"
  }'
```

#### Delete a task

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { deleteTask(id: 1) }"
  }'
```

---

### State-Transition Mutations ⚡

These are the highlight of the project — intent-revealing mutations that change task status while enforcing business rules.

#### Start a task (TODO → IN_PROGRESS)

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { startTask(id: 1) { id title status } }"
  }'
```

> Returns an error if the task is not in `TODO` status.

#### Complete a task (TODO or IN_PROGRESS → DONE)

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { completeTask(id: 1) { id title status } }"
  }'
```

> Returns an error if the task is already `DONE`.

#### Reopen a task (DONE → TODO)

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { reopenTask(id: 1) { id title status } }"
  }'
```

> Returns an error if the task is not in `DONE` status.

---

### Query Examples

#### List all projects with their tasks

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { projects { id name description tasks { id title status priority } } }"
  }'
```

#### Get a single project by ID

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { project(id: 1) { id name tasks { title status } } }"
  }'
```

#### Get tasks filtered by status

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { tasksByStatus(status: IN_PROGRESS) { id title status project { name } } }"
  }'
```

#### Get tasks in a project filtered by status (kanban board view)

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { tasksByProjectAndStatus(projectId: 1, status: TODO) { id title priority } }"
  }'
```

#### Search projects by name

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { searchProjects(name: \"Backend\") { id name description } }"
  }'
```

---

## Running Tests

The test suite has two layers:

### Unit Tests (fast, no Docker required)

Unit tests run against mocked repositories — no database or Spring context is needed.

```bash
./mvnw test -Dtest="*Test" -DfailIfNoTests=false
```

### Integration Tests (requires Docker)

Integration tests spin up a real PostgreSQL container via **Testcontainers** and exercise the full stack.

```bash
./mvnw test -Dtest="*IntegrationTest" -DfailIfNoTests=false
```

### Run all tests

```bash
./mvnw clean test
```

### Test structure

```
src/test/java/
└── com/example/graphqlmutations/
    ├── domain/
    │   ├── ProjectTest.java              # Unit tests for the Project entity
    │   └── TaskTest.java                 # Unit tests for the Task entity
    ├── service/
    │   ├── ProjectServiceTest.java       # Unit tests for ProjectService (Mockito)
    │   └── TaskServiceTest.java          # Unit tests for TaskService (Mockito)
    │                                     # Includes full state-transition tests
    └── GraphqlMutationsIntegrationTest.java  # Full integration tests (Testcontainers)
```

## Project Structure

```
19-graphql-mutations/
├── src/
│   ├── main/
│   │   ├── java/com/example/graphqlmutations/
│   │   │   ├── GraphqlMutationsApplication.java   # Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   └── GraphQlConfig.java             # Registers custom GraphQL scalars
│   │   │   ├── controller/
│   │   │   │   ├── ProjectController.java         # GraphQL resolvers for Project
│   │   │   │   └── TaskController.java            # GraphQL resolvers for Task
│   │   │   ├── domain/
│   │   │   │   ├── Project.java                   # JPA entity
│   │   │   │   ├── Task.java                      # JPA entity
│   │   │   │   └── TaskStatus.java                # Enum: TODO, IN_PROGRESS, DONE
│   │   │   ├── dto/
│   │   │   │   ├── ProjectInput.java              # GraphQL input type for Project
│   │   │   │   └── TaskInput.java                 # GraphQL input type for Task
│   │   │   ├── repository/
│   │   │   │   ├── ProjectRepository.java         # Spring Data JPA repository
│   │   │   │   └── TaskRepository.java            # Spring Data JPA repository
│   │   │   └── service/
│   │   │       ├── ProjectService.java            # Business logic for Projects
│   │   │       └── TaskService.java               # Business logic + state transitions
│   │   └── resources/
│   │       ├── application.yml                    # Application configuration
│   │       └── graphql/
│   │           └── schema.graphqls                # GraphQL schema definition
│   └── test/
│       ├── java/com/example/graphqlmutations/     # Tests (see above)
│       └── resources/
│           ├── application-test.yml               # Test profile configuration
│           ├── docker-java.properties             # Docker API version for Testcontainers
│           └── testcontainers.properties          # Testcontainers Docker API config
├── .gitignore
├── docker-compose.yml                             # PostgreSQL + App services
├── Dockerfile                                     # Multi-stage build
├── mvnw / mvnw.cmd                                # Maven wrapper
├── pom.xml                                        # Project dependencies
└── README.md
```
