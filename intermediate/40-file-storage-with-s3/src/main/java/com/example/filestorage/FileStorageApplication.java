package com.example.filestorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the File Storage with S3 mini-project.
 *
 * <p>This application demonstrates how to upload, download, list, and delete
 * files using the AWS S3 API. It is compatible with any S3-compatible storage
 * backend, including Amazon S3 and MinIO (used locally via Docker Compose).
 *
 * <p>Key design choices:
 * <ul>
 *   <li>AWS SDK v2 (software.amazon.awssdk) – the modern, non-blocking-capable SDK.</li>
 *   <li>Endpoint override – allows pointing the SDK at a local MinIO instance
 *       instead of the real AWS endpoint.</li>
 *   <li>Path-style access – required by MinIO (virtual-hosted-style is AWS-only by default).</li>
 * </ul>
 */
@SpringBootApplication
public class FileStorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileStorageApplication.class, args);
    }
}
