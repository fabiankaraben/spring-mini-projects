package com.example.fileupload.service;

import com.example.fileupload.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit testing for the FileStorageService logic using JUnit 5.
 */
class FileStorageServiceTest {

    // Helper annotation to handle temporary directories creation and cleanup
    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        // Init the service pointing to a temp dir to isolate file operations.
        service = new FileStorageService(tempDir.toString());
        service.init();
    }

    @Test
    void saveFileSuccessfully() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "Hello, World".getBytes());

        // Act
        service.store(file);

        // Assert
        assertTrue(Files.exists(tempDir.resolve("hello.txt")));
    }

    @Test
    void saveFileFailsWhenEmpty() {
        // Arrange
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        // Act & Assert
        StorageException exception = assertThrows(StorageException.class, () -> service.store(emptyFile));
        assertEquals("Failed to store empty file.", exception.getMessage());
    }

    @Test
    void saveFileFailsWithRelativePath() {
        // Arrange
        MockMultipartFile maliciousFile = new MockMultipartFile(
                "file", "../malicious.txt", MediaType.TEXT_PLAIN_VALUE, "malicious data".getBytes());

        // Act & Assert
        StorageException exception = assertThrows(StorageException.class, () -> service.store(maliciousFile));
        assertTrue(exception.getMessage().contains("Cannot store file with relative path outside current directory"));
    }
}
