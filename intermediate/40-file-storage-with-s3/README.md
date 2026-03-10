# File Storage with S3 / MinIO

A Spring Boot backend that uploads and downloads files using the AWS S3 API.
It is fully compatible with any S3-compatible storage backend, including
**Amazon S3** (cloud) and **MinIO** (self-hosted, run locally via Docker Compose).

---

## What this project demonstrates

- Configuring the **AWS SDK v2** (`software.amazon.awssdk`) with Spring Boot.
- Using an **endpoint override** to point the SDK at a local MinIO instance instead of AWS.
- Enabling **path-style access** required by MinIO.
- Exposing a clean REST API for upload, download, list, delete, and metadata operations.
- Translating SDK exceptions into **domain exceptions** (`FileNotFoundException`, `StorageException`) using `@RestControllerAdvice`.
- Returning RFC 7807 **ProblemDetail** error responses.
- Writing **unit tests** (Mockito, no Docker) for service business logic.
- Writing **integration tests** (Testcontainers + real MinIO container) for end-to-end behaviour.

---

## Requirements

| Tool | Minimum version |
|------|----------------|
| Java | 21 |
| Maven | 3.9+ (or use the included Maven Wrapper) |
| Docker | 24+ with Docker Desktop 4+ |

> **Note:** Docker is required to run the application (MinIO via Docker Compose) and to run the integration tests (Testcontainers starts MinIO automatically).

---

## Project structure

```
src/
├── main/
│   ├── java/com/example/filestorage/
│   │   ├── FileStorageApplication.java      # Spring Boot entry point
│   │   ├── config/
│   │   │   └── S3Config.java                # AWS S3Client bean configuration
│   │   ├── controller/
│   │   │   └── FileStorageController.java   # REST endpoints
│   │   ├── domain/
│   │   │   ├── FileMetadata.java            # Value object: file metadata
│   │   │   └── UploadResult.java            # Value object: upload result
│   │   ├── exception/
│   │   │   ├── FileNotFoundException.java   # Domain exception → HTTP 404
│   │   │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice
│   │   │   └── StorageException.java        # Domain exception → HTTP 500
│   │   └── service/
│   │       └── FileStorageService.java      # Core business logic (S3 operations)
│   └── resources/
│       └── application.yml                  # App configuration
└── test/
    ├── java/com/example/filestorage/
    │   ├── domain/
    │   │   └── FileMetadataTest.java         # Unit tests: domain record
    │   ├── service/
    │   │   └── FileStorageServiceTest.java   # Unit tests: service (Mockito)
    │   └── integration/
    │       └── FileStorageIntegrationTest.java  # Integration tests (Testcontainers)
    └── resources/
        ├── docker-java.properties            # Docker API version fix for Docker Desktop 29+
        └── testcontainers.properties         # Testcontainers config
```

---

## Running with Docker Compose

The entire stack (MinIO + Spring Boot app) runs with a single command:

```bash
# Build images and start all services (MinIO + app)
docker compose up --build

# Stop and remove containers
docker compose down

# Stop and also remove the MinIO data volume
docker compose down -v
```

After startup:
- **REST API:** `http://localhost:8080/api/files`
- **MinIO Web Console:** `http://localhost:9001` (user: `minioadmin`, password: `minioadmin`)

---

## Running locally (without Docker for the app)

If you want to run the Spring Boot application directly on your machine but still need MinIO:

```bash
# 1. Start only MinIO
docker compose up minio

# 2. Run the Spring Boot app locally
./mvnw spring-boot:run
```

---

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/files/upload` | Upload a file (multipart/form-data) |
| `GET` | `/api/files` | List all files in the bucket |
| `GET` | `/api/files/{key}/download` | Download a file by its key |
| `GET` | `/api/files/{key}/metadata` | Get metadata for a file |
| `DELETE` | `/api/files/{key}` | Delete a file by its key |

---

## Usage examples (curl)

### Upload a file

```bash
curl -X POST http://localhost:8080/api/files/upload \
     -F "file=@/path/to/your/photo.jpg"
```

Response:
```json
{
  "bucket": "file-storage-bucket",
  "key": "3f2504e0-4f89-11d3-9a0c-0305e82c3301-photo.jpg",
  "etag": "\"d41d8cd98f00b204e9800998ecf8427e\""
}
```

> Save the `key` value – you will need it for download, metadata, and delete operations.

---

### List all files

```bash
curl http://localhost:8080/api/files
```

Response:
```json
[
  {
    "key": "3f2504e0-4f89-11d3-9a0c-0305e82c3301-photo.jpg",
    "size": 102400,
    "contentType": "application/octet-stream",
    "lastModified": "2024-01-20T10:30:00Z",
    "etag": "\"d41d8cd98f00b204e9800998ecf8427e\""
  }
]
```

---

### Download a file

Replace `<key>` with the key returned from the upload response.

```bash
curl -O -J "http://localhost:8080/api/files/<key>/download"

# Example:
curl -O -J "http://localhost:8080/api/files/3f2504e0-4f89-11d3-9a0c-0305e82c3301-photo.jpg/download"
```

---

### Get file metadata

```bash
curl "http://localhost:8080/api/files/<key>/metadata"

# Example:
curl "http://localhost:8080/api/files/3f2504e0-4f89-11d3-9a0c-0305e82c3301-photo.jpg/metadata"
```

Response:
```json
{
  "key": "3f2504e0-4f89-11d3-9a0c-0305e82c3301-photo.jpg",
  "size": 102400,
  "contentType": "image/jpeg",
  "lastModified": "2024-01-20T10:30:00Z",
  "etag": "\"d41d8cd98f00b204e9800998ecf8427e\""
}
```

---

### Delete a file

```bash
curl -X DELETE "http://localhost:8080/api/files/<key>"

# Example:
curl -X DELETE "http://localhost:8080/api/files/3f2504e0-4f89-11d3-9a0c-0305e82c3301-photo.jpg"
```

Returns HTTP `204 No Content` on success.

---

## Error responses

All errors follow the [RFC 7807 Problem Detail](https://datatracker.ietf.org/doc/html/rfc7807) format:

```json
{
  "type": "about:blank",
  "title": "File Not Found",
  "status": 404,
  "detail": "File not found in bucket: ghost.txt"
}
```

| Scenario | HTTP Status |
|----------|------------|
| File key does not exist | `404 Not Found` |
| Storage infrastructure error | `500 Internal Server Error` |

---

## Running the tests

> Docker must be running because the integration tests use Testcontainers to spin up a real MinIO container.

```bash
./mvnw clean test
```

### Test categories

| Test class | Type | Description |
|-----------|------|-------------|
| `FileMetadataTest` | Unit | Tests the domain record (no I/O, no Spring) |
| `FileStorageServiceTest` | Unit | Tests service logic with a mocked S3Client |
| `FileStorageIntegrationTest` | Integration | Tests against a real MinIO container via Testcontainers |

### Unit tests only (no Docker required)

```bash
./mvnw test -Dtest="FileMetadataTest,FileStorageServiceTest"
```

### Integration tests only

```bash
./mvnw test -Dtest="FileStorageIntegrationTest"
```

---

## Configuration reference

All settings live in `src/main/resources/application.yml`:

| Property | Default | Description |
|---------|---------|-------------|
| `app.s3.endpoint-url` | `http://localhost:9000` | S3-compatible endpoint URL |
| `app.s3.region` | `us-east-1` | AWS region (required by SDK, ignored by MinIO) |
| `app.s3.access-key` | `minioadmin` | Access key ID |
| `app.s3.secret-key` | `minioadmin` | Secret access key |
| `app.s3.bucket-name` | `file-storage-bucket` | Bucket to store files in |
| `app.s3.path-style-access` | `true` | `true` for MinIO, `false` for AWS virtual-hosted style |

### Using with real AWS S3

Override the properties via environment variables when deploying:

```bash
export APP_S3_ENDPOINT_URL=https://s3.amazonaws.com
export APP_S3_REGION=eu-west-1
export APP_S3_ACCESS_KEY=AKIAIOSFODNN7EXAMPLE
export APP_S3_SECRET_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
export APP_S3_BUCKET_NAME=my-production-bucket
export APP_S3_PATH_STYLE_ACCESS=false
```

---

## MinIO Web Console

When running via Docker Compose, you can manage buckets and objects visually:

1. Open `http://localhost:9001` in your browser.
2. Log in with username `minioadmin` and password `minioadmin`.
3. Navigate to **Buckets** → `file-storage-bucket` to see uploaded files.
