package com.example.filestorage.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link FileMetadata} domain record.
 *
 * <p>These tests verify the pure domain object behaviour – no Spring context,
 * no mocks, no network. They run instantly and serve as documentation of the
 * expected contract of the value object.
 */
@DisplayName("FileMetadata domain record")
class FileMetadataTest {

    @Test
    @DisplayName("should store all constructor arguments and expose them via accessors")
    void shouldStoreAllFields() {
        // Arrange
        Instant now = Instant.now();

        // Act – create a FileMetadata record via its canonical constructor
        FileMetadata metadata = new FileMetadata(
                "photos/cat.jpg",
                102_400L,
                "image/jpeg",
                now,
                "\"abc123\""
        );

        // Assert – each record component accessor returns the value passed to the constructor
        assertThat(metadata.key()).isEqualTo("photos/cat.jpg");
        assertThat(metadata.size()).isEqualTo(102_400L);
        assertThat(metadata.contentType()).isEqualTo("image/jpeg");
        assertThat(metadata.lastModified()).isEqualTo(now);
        assertThat(metadata.etag()).isEqualTo("\"abc123\"");
    }

    @Test
    @DisplayName("should implement value equality based on all fields")
    void shouldImplementValueEquality() {
        // Arrange
        Instant now = Instant.now();
        FileMetadata a = new FileMetadata("key.txt", 10L, "text/plain", now, "\"etag\"");
        FileMetadata b = new FileMetadata("key.txt", 10L, "text/plain", now, "\"etag\"");

        // Assert – Java records auto-generate equals() based on all components
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("should not be equal when any field differs")
    void shouldNotBeEqualWhenFieldsDiffer() {
        Instant now = Instant.now();
        FileMetadata base = new FileMetadata("key.txt", 10L, "text/plain", now, "\"etag\"");

        // Different key
        assertThat(base).isNotEqualTo(
                new FileMetadata("other.txt", 10L, "text/plain", now, "\"etag\""));
        // Different size
        assertThat(base).isNotEqualTo(
                new FileMetadata("key.txt", 999L, "text/plain", now, "\"etag\""));
        // Different contentType
        assertThat(base).isNotEqualTo(
                new FileMetadata("key.txt", 10L, "application/octet-stream", now, "\"etag\""));
    }

    @Test
    @DisplayName("toString should contain all field values")
    void toStringShouldContainAllFields() {
        Instant now = Instant.now();
        FileMetadata metadata = new FileMetadata("report.pdf", 5000L, "application/pdf", now, "\"xyz\"");

        String str = metadata.toString();

        // Java records auto-generate toString() in the format TypeName[field=value, ...]
        assertThat(str).contains("report.pdf");
        assertThat(str).contains("5000");
        assertThat(str).contains("application/pdf");
    }
}
