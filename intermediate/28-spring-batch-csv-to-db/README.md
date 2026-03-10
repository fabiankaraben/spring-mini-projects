# Spring Batch CSV to DB

A Spring Boot backend that reads a CSV file and stores valid rows into a PostgreSQL database using **Spring Batch**. Invalid rows are silently skipped and logged — the job always runs to completion.

## What This Mini-Project Demonstrates

- **Spring Batch** chunk-oriented processing: `FlatFileItemReader` → `ItemProcessor` → `JpaItemWriter`
- **Row validation** inside the `ItemProcessor` (blank fields, email format, positive salary, date parsing)
- **Filtering** invalid rows (returning `null` from the processor skips the item without failing the job)
- **Flyway** database migrations for the application schema and Spring Batch meta-data tables
- **REST API** to trigger the batch job on demand and query imported data
- **Unit tests** for the processor with JUnit 5 (no Spring context needed)
- **Integration tests** with Testcontainers (real PostgreSQL, full Spring context, real Flyway migrations)

## Architecture

```
CSV File
   │
   ▼
FlatFileItemReader       – reads one line at a time, maps to EmployeeCsvRow
   │
   ▼
EmployeeItemProcessor    – validates & converts to Employee entity (returns null = skip)
   │
   ▼
JpaItemWriter            – flushes a chunk of Employee entities to PostgreSQL
   │
   ▼
PostgreSQL (employees table)
```

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use the included `./mvnw` wrapper) |
| Docker & Docker Compose | For running via Docker Compose |

## CSV File Format

The CSV must have the following header row. All fields are required unless noted:

```
first_name,last_name,email,department,salary,hire_date
```

| Column | Type | Rules |
|---|---|---|
| `first_name` | String | Non-blank |
| `last_name` | String | Non-blank |
| `email` | String | Non-blank, must contain `@`, must be unique |
| `department` | String | Non-blank |
| `salary` | Decimal | Positive number (e.g. `95000.00`) |
| `hire_date` | Date | Format `yyyy-MM-dd` (e.g. `2021-03-15`) |

A sample file with valid and intentionally invalid rows is provided at `data/employees.csv`.

## Running with Docker Compose (recommended)

This is the fully self-contained way to run the project. Docker Compose starts PostgreSQL and the Spring Boot application together.

### 1. Build and start all services

```bash
docker compose up --build
```

This will:
1. Build the Spring Boot application image (multi-stage Dockerfile)
2. Start a PostgreSQL 16 container
3. Run Flyway migrations (creates `employees` table and Spring Batch meta-data tables)
4. Start the Spring Boot application on port `8080`

### 2. Place your CSV file

The `./data/` directory is bind-mounted into the container at `/data/`. The application reads from `file:/data/employees.csv` when running in Docker.

A sample `data/employees.csv` file is already included. Replace it with your own data as needed — **no rebuild required**.

### 3. Trigger the batch job

```bash
curl -X POST http://localhost:8080/api/batch/jobs/import-employees
```

### 4. Stop all services

```bash
docker compose down
```

To also remove the PostgreSQL data volume (start from scratch):

```bash
docker compose down -v
```

## Running Locally (without Docker)

### Prerequisites

- Java 21+
- PostgreSQL running locally on port `5432` with:
  - Database: `batchdb`
  - Username: `batch`
  - Password: `batch`

### Start the application

```bash
./mvnw spring-boot:run
```

Flyway will automatically create the required tables on startup. The default CSV path is `classpath:data/employees.csv` (the bundled sample file).

## REST API

### Trigger the CSV import job

Launches the `importEmployeesJob` batch job. The job runs synchronously and returns a summary when complete.

```bash
curl -X POST http://localhost:8080/api/batch/jobs/import-employees
```

**Response example:**

```json
{
  "jobName": "importEmployeesJob",
  "jobExecutionId": 1,
  "status": "COMPLETED",
  "readCount": 20,
  "writeCount": 17,
  "skipCount": 0,
  "startTime": "2024-01-15T10:30:00.123",
  "endTime": "2024-01-15T10:30:01.456"
}
```

> **Note:** `skipCount` reflects items skipped via `skip()` policy. Invalid rows filtered by the processor (returning `null`) appear in `writeCount` as filtered items and are not counted as skips.

### List all employees

```bash
curl http://localhost:8080/api/employees
```

### Get a single employee by ID

```bash
curl http://localhost:8080/api/employees/1
```

### Filter employees by department

```bash
curl http://localhost:8080/api/employees/department/Engineering
```

### Get total employee count

```bash
curl http://localhost:8080/api/employees/count
```

**Response:**

```json
{ "count": 17 }
```

### Health check

```bash
curl http://localhost:8080/actuator/health
```

## Running Multiple Times

The job uses a `RunIdIncrementer` which appends a unique timestamp to each job execution's parameters. This allows the same job to be re-triggered via `POST /api/batch/jobs/import-employees` without Spring Batch blocking it as a duplicate run.

> **Warning:** Re-running the job against the same data will fail on the `email UNIQUE` constraint for already-imported rows. Clear the `employees` table first if you want to re-import:
> ```bash
> # Connect to PostgreSQL and truncate
> psql -h localhost -p 5432 -U batch -d batchdb -c "TRUNCATE TABLE employees;"
> ```

## Providing a Custom CSV File

### In Docker Compose

Place your file at `./data/employees.csv` (relative to the project root). The directory is already bind-mounted. No rebuild is needed.

### Locally (outside Docker)

Set the `batch.csv-file-path` property to an absolute path:

```bash
./mvnw spring-boot:run -Dbatch.csv-file-path=file:/path/to/your/employees.csv
```

Or use the `file:` prefix for an absolute path on the filesystem:

```bash
BATCH_CSV_FILE_PATH=file:/path/to/your/employees.csv ./mvnw spring-boot:run
```

## Database Schema

### `employees` table

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGSERIAL` | Primary key |
| `first_name` | `VARCHAR(100)` | NOT NULL |
| `last_name` | `VARCHAR(100)` | NOT NULL |
| `email` | `VARCHAR(200)` | NOT NULL, UNIQUE |
| `department` | `VARCHAR(100)` | NOT NULL |
| `salary` | `NUMERIC(12,2)` | NOT NULL |
| `hire_date` | `DATE` | NOT NULL |

### Spring Batch meta-data tables

Spring Batch requires a set of tables to track job executions, step executions, and job instances. These are created by Flyway migration `V2__create_spring_batch_tables.sql`:

- `BATCH_JOB_INSTANCE`
- `BATCH_JOB_EXECUTION`
- `BATCH_JOB_EXECUTION_PARAMS`
- `BATCH_STEP_EXECUTION`
- `BATCH_STEP_EXECUTION_CONTEXT`
- `BATCH_JOB_EXECUTION_CONTEXT`

## Running Tests

### All tests (unit + integration)

Integration tests require Docker to be running (Testcontainers will automatically pull and start a `postgres:16-alpine` container).

```bash
./mvnw clean test
```

### Unit tests only (no Docker required)

```bash
./mvnw test -Dtest="*Test" -DfailIfNoTests=false
```

### Integration tests only

```bash
./mvnw test -Dtest="*IntegrationTest" -DfailIfNoTests=false
```

## Test Coverage

### Unit Tests — `EmployeeItemProcessorTest`

Tests the `EmployeeItemProcessor` in complete isolation (no Spring context, no database):

| Test | Description |
|---|---|
| `process_validRow_returnsEmployee` | Valid row produces a correct `Employee` entity |
| `process_rowWithWhitespace_trimmedCorrectly` | Leading/trailing whitespace is trimmed |
| `process_blankFirstName_returnsNull` | Blank `firstName` is rejected |
| `process_nullFirstName_returnsNull` | Null `firstName` is rejected |
| `process_blankLastName_returnsNull` | Blank `lastName` is rejected |
| `process_nullLastName_returnsNull` | Null `lastName` is rejected |
| `process_blankEmail_returnsNull` | Blank `email` is rejected |
| `process_emailWithoutAtSign_returnsNull` | Email without `@` is rejected |
| `process_nullEmail_returnsNull` | Null `email` is rejected |
| `process_blankDepartment_returnsNull` | Blank `department` is rejected |
| `process_nullDepartment_returnsNull` | Null `department` is rejected |
| `process_invalidSalary_returnsNull` | Non-numeric salary is rejected |
| `process_zeroSalary_returnsNull` | Zero salary is rejected |
| `process_negativeSalary_returnsNull` | Negative salary is rejected |
| `process_blankSalary_returnsNull` | Blank salary is rejected |
| `process_invalidDateFormat_returnsNull` | Wrong date format is rejected |
| `process_nonDateHireDate_returnsNull` | Non-date hire date is rejected |
| `process_blankHireDate_returnsNull` | Blank hire date is rejected |
| `process_salaryWithDecimals_parsedCorrectly` | Decimal salary parsed correctly |

### Integration Tests — `ImportEmployeesJobIntegrationTest`

Tests the full batch job end-to-end against a real PostgreSQL database (Testcontainers):

| Test | Description |
|---|---|
| `importJob_completesSuccessfully` | Job exits with `COMPLETED` status |
| `importJob_persistsValidRows` | All 17 valid rows from the sample CSV are stored |
| `importJob_skipsInvalidRows` | Invalid rows are not persisted |
| `importJob_storesNamesCorrectly` | First and last name are stored correctly |
| `importJob_storesDepartmentAndSalary` | Department and salary are stored correctly |
| `importJob_storesHireDateCorrectly` | Hire date is parsed and stored correctly |
| `findByDepartment_returnsOnlyMatchingEmployees` | Repository returns only Engineering employees |
| `importJob_stepExecutionCounts` | Step reports 20 reads, 17 writes, 3 filtered |

## Project Structure

```
28-spring-batch-csv-to-db/
├── data/
│   └── employees.csv                          # Sample CSV (bind-mounted in Docker Compose)
├── src/
│   ├── main/
│   │   ├── java/com/example/batchcsvtodb/
│   │   │   ├── SpringBatchCsvToDbApplication.java   # @SpringBootApplication entry point
│   │   │   ├── batch/
│   │   │   │   ├── BatchJobConfig.java              # Job, Step, Reader, Writer beans
│   │   │   │   ├── EmployeeCsvRow.java              # DTO for raw CSV row
│   │   │   │   └── EmployeeItemProcessor.java       # Validation & transformation
│   │   │   ├── controller/
│   │   │   │   └── BatchJobController.java          # REST endpoints
│   │   │   ├── model/
│   │   │   │   └── Employee.java                    # JPA entity
│   │   │   └── repository/
│   │   │       └── EmployeeRepository.java          # Spring Data JPA repository
│   │   └── resources/
│   │       ├── application.yml                      # Main configuration
│   │       ├── data/
│   │       │   └── employees.csv                    # Bundled sample CSV (classpath)
│   │       └── db/migration/
│   │           ├── V1__create_employees_table.sql   # Flyway: employees schema
│   │           └── V2__create_spring_batch_tables.sql # Flyway: Batch meta-data tables
│   └── test/
│       ├── java/com/example/batchcsvtodb/
│       │   ├── batch/
│       │   │   └── EmployeeItemProcessorTest.java   # Unit tests (no Spring context)
│       │   └── integration/
│       │       └── ImportEmployeesJobIntegrationTest.java # Testcontainers integration tests
│       └── resources/
│           ├── application-integration-test.yml     # Integration test profile config
│           ├── docker-java.properties               # Docker API version fix for Testcontainers
│           └── testcontainers.properties            # Testcontainers Docker API config
├── .gitignore
├── docker-compose.yml                           # PostgreSQL + app services
├── Dockerfile                                   # Multi-stage build
├── mvnw / mvnw.cmd                              # Maven wrapper scripts
├── pom.xml
└── README.md
```
