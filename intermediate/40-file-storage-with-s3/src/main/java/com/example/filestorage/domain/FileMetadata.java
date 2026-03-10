package com.example.filestorage.domain;

import java.time.Instant;

/**
 * Immutable value object that holds metadata about a stored file.
 *
 * <p>This is a pure domain object with no dependencies on any framework or SDK.
 * It represents the information we return to API callers when they list or upload files.
 *
 * <p>Using Java 16+ records keeps the domain model concise and inherently immutable.
 *
 * @param key          the object key (path) inside the S3 bucket, e.g. {@code photos/cat.jpg}
 * @param size         the file size in bytes
 * @param contentType  the MIME content type, e.g. {@code image/jpeg}
 * @param lastModified the timestamp when the object was last modified in the bucket
 * @param etag         the entity tag returned by S3 / MinIO (MD5 of the content for non-multipart uploads)
 */
public record FileMetadata(
        String key,
        long size,
        String contentType,
        Instant lastModified,
        String etag
) {
}
