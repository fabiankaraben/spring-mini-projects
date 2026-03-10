package com.example.filestorage.integration;

import com.example.filestorage.domain.FileMetadata;
import com.example.filestorage.domain.UploadResult;
import com.example.filestorage.exception.FileNotFoundException;
import com.example.filestorage.service.FileStorageService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full integration tests for {@link FileStorageService} using a real MinIO server
 * managed by Testcontainers.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Testcontainers starts a real MinIO Docker container before any test runs.</li>
 *   <li>{@link DynamicPropertySource} injects the container's dynamic host/port into
 *       Spring's {@code app.s3.*} properties, overriding the values in
 *       {@code application.yml}.</li>
 *   <li>Spring Boot starts with a real {@code S3Client} pointing at the MinIO container.</li>
 *   <li>Each test exercises the full stack: HTTP → Spring → S3Client → MinIO container.</li>
 * </ol>
 *
 * <h2>Container lifecycle</h2>
 * <p>The {@code @Container} static field causes Testcontainers to start the MinIO
 * container once for the entire test class (not once per test method). This avoids
 * the overhead of starting/stopping a container for each test.
 *
 * <p>The {@code @BeforeEach} method creates a fresh bucket and uploads/deletes objects
 * as needed to keep tests isolated from each other.
 */
@SpringBootTest
@Testcontainers
@DisplayName("FileStorageService integration tests (MinIO via Testcontainers)")
class FileStorageIntegrationTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Container setup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * MinIO Testcontainer – starts once per class (static field + @Container).
     *
     * <p>Uses the official MinIO Docker image. The {@code MinIOContainer} class
     * from the Testcontainers MinIO module provides convenience methods for
     * retrieving the dynamic S3 endpoint URL and credentials.
     */
    @Container
    static MinIOContainer minioContainer = new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z")
            .withUserName("minioadmin")
            .withPassword("minioadmin");

    /**
     * Overrides the Spring Boot application properties at runtime with the
     * MinIO container's dynamically assigned host and port.
     *
     * <p>This is the recommended Testcontainers + Spring Boot pattern for
     * injecting container coordinates without hardcoding ports.
     *
     * @param registry the Spring property registry to populate
     */
    @DynamicPropertySource
    static void configureMinioProperties(DynamicPropertyRegistry registry) {
        // Override endpoint to point at the Testcontainers MinIO instance
        registry.add("app.s3.endpoint-url", minioContainer::getS3URL);
        registry.add("app.s3.access-key", minioContainer::getUserName);
        registry.add("app.s3.secret-key", minioContainer::getPassword);
        // Use a separate bucket name for integration tests to avoid conflicts
        registry.add("app.s3.bucket-name", () -> "integration-test-bucket");
        registry.add("app.s3.region", () -> "us-east-1");
        registry.add("app.s3.path-style-access", () -> "true");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test setup
    // ─────────────────────────────────────────────────────────────────────────

    /** The real service bean wired with the real S3Client pointing at MinIO. */
    @Autowired
    private FileStorageService fileStorageService;

    /**
     * Ensures the test bucket exists before each test.
     * Because the container is shared across test methods, the bucket is created
     * only once (ensureBucketExists is idempotent).
     */
    @BeforeEach
    void setUp() {
        fileStorageService.ensureBucketExists();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should upload a file and return a non-null key and etag")
    void shouldUploadFile() {
        // Arrange – create an in-memory multipart file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                "Hello, MinIO!".getBytes()
        );

        // Act
        UploadResult result = fileStorageService.uploadFile(file);

        // Assert – bucket name matches, key ends with original filename, etag present
        assertThat(result.bucket()).isEqualTo("integration-test-bucket");
        assertThat(result.key()).endsWith("hello.txt");
        assertThat(result.etag()).isNotBlank();
    }

    @Test
    @DisplayName("should download a previously uploaded file with matching content")
    void shouldDownloadFile() throws IOException {
        // Arrange – upload a file first
        byte[] content = "Download me!".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "download.txt", "text/plain", content);
        UploadResult uploadResult = fileStorageService.uploadFile(file);

        // Act – download using the key returned by upload
        InputStream downloadStream = fileStorageService.downloadFile(uploadResult.key());

        // Assert – content matches what was uploaded
        byte[] downloaded = downloadStream.readAllBytes();
        assertThat(downloaded).isEqualTo(content);
    }

    @Test
    @DisplayName("should list all uploaded files in the bucket")
    void shouldListFiles() {
        // Arrange – upload two distinct files
        fileStorageService.uploadFile(new MockMultipartFile(
                "file", "list-test-a.txt", "text/plain", "File A".getBytes()));
        fileStorageService.uploadFile(new MockMultipartFile(
                "file", "list-test-b.txt", "text/plain", "File B".getBytes()));

        // Act
        List<FileMetadata> files = fileStorageService.listFiles();

        // Assert – at least two files are present (bucket is shared across test methods)
        assertThat(files).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("should retrieve metadata for an existing file")
    void shouldGetFileMetadata() {
        // Arrange
        byte[] content = "Metadata test content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "metadata.txt", "text/plain", content);
        UploadResult uploadResult = fileStorageService.uploadFile(file);

        // Act
        FileMetadata metadata = fileStorageService.getFileMetadata(uploadResult.key());

        // Assert – size matches the content length, key matches
        assertThat(metadata.key()).isEqualTo(uploadResult.key());
        assertThat(metadata.size()).isEqualTo(content.length);
        assertThat(metadata.contentType()).isEqualTo("text/plain");
        assertThat(metadata.etag()).isEqualTo(uploadResult.etag());
    }

    @Test
    @DisplayName("should delete a file and make it no longer accessible")
    void shouldDeleteFile() {
        // Arrange – upload a file so we have something to delete
        MockMultipartFile file = new MockMultipartFile(
                "file", "to-delete.txt", "text/plain", "Delete me".getBytes());
        UploadResult uploadResult = fileStorageService.uploadFile(file);

        // Act – delete the uploaded file
        fileStorageService.deleteFile(uploadResult.key());

        // Assert – subsequent download should throw FileNotFoundException
        assertThatThrownBy(() -> fileStorageService.downloadFile(uploadResult.key()))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("should throw FileNotFoundException when downloading a non-existent key")
    void shouldThrowFileNotFoundForMissingKey() {
        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.downloadFile("non-existent-key.txt"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("non-existent-key.txt");
    }

    @Test
    @DisplayName("should throw FileNotFoundException when requesting metadata for a non-existent key")
    void shouldThrowFileNotFoundForMissingMetadata() {
        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.getFileMetadata("ghost-file.txt"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("ghost-file.txt");
    }

    @Test
    @DisplayName("should upload multiple files and retrieve each one independently")
    void shouldHandleMultipleFilesIndependently() throws IOException {
        // Arrange
        byte[] content1 = "First file content".getBytes();
        byte[] content2 = "Second file content".getBytes();

        UploadResult result1 = fileStorageService.uploadFile(
                new MockMultipartFile("file", "multi1.txt", "text/plain", content1));
        UploadResult result2 = fileStorageService.uploadFile(
                new MockMultipartFile("file", "multi2.txt", "text/plain", content2));

        // Act & Assert – each file's content can be independently retrieved
        try (InputStream s1 = fileStorageService.downloadFile(result1.key())) {
            assertThat(s1.readAllBytes()).isEqualTo(content1);
        }
        try (InputStream s2 = fileStorageService.downloadFile(result2.key())) {
            assertThat(s2.readAllBytes()).isEqualTo(content2);
        }
    }
}
