package com.example.filestorage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

import java.net.URI;

/**
 * Spring configuration class that creates and configures the AWS S3 client bean.
 *
 * <p>The client is configured to work with both:
 * <ul>
 *   <li><strong>Amazon S3</strong> – using real AWS credentials and no endpoint override.</li>
 *   <li><strong>MinIO</strong> – using an endpoint override pointing to the MinIO server
 *       (e.g., {@code http://localhost:9000}), which provides an S3-compatible API.</li>
 * </ul>
 *
 * <p>All values are injected from {@code application.yml} so the same codebase works
 * in local development (MinIO via Docker Compose) and production (real AWS S3).
 */
@Configuration
public class S3Config {

    /**
     * The S3-compatible endpoint URL.
     * For MinIO: {@code http://localhost:9000}.
     * For AWS: leave blank or omit – the SDK resolves it automatically.
     */
    @Value("${app.s3.endpoint-url}")
    private String endpointUrl;

    /**
     * AWS region (e.g., {@code us-east-1}).
     * MinIO ignores region but the SDK requires a non-null value.
     */
    @Value("${app.s3.region}")
    private String region;

    /**
     * Access key ID – corresponds to MinIO's MINIO_ROOT_USER or AWS access key.
     */
    @Value("${app.s3.access-key}")
    private String accessKey;

    /**
     * Secret access key – corresponds to MinIO's MINIO_ROOT_PASSWORD or AWS secret key.
     */
    @Value("${app.s3.secret-key}")
    private String secretKey;

    /**
     * Whether to use path-style access ({@code true} for MinIO, {@code false} for AWS virtual-hosted style).
     *
     * <p>Path-style URL example:  {@code http://localhost:9000/my-bucket/my-object}
     * <p>Virtual-hosted-style:    {@code http://my-bucket.s3.amazonaws.com/my-object}
     */
    @Value("${app.s3.path-style-access}")
    private boolean pathStyleAccess;

    /**
     * Builds and returns the {@link S3Client} bean used throughout the application.
     *
     * <p>The URL Connection HTTP client is used because it has no additional
     * transitive dependencies, unlike the default Apache HTTP client.
     *
     * @return a fully configured {@link S3Client}
     */
    @Bean
    public S3Client s3Client() {
        // Build static credentials from configured access/secret keys
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                // Use a lightweight HTTP client (no extra dependencies)
                .httpClient(UrlConnectionHttpClient.builder().build())
                // Provide credentials statically (no environment variable / profile lookup)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                // Set the AWS region (required even for MinIO)
                .region(Region.of(region))
                // Override the endpoint so the SDK talks to MinIO instead of AWS
                .endpointOverride(URI.create(endpointUrl))
                // Enable path-style access required by MinIO
                .forcePathStyle(pathStyleAccess)
                .build();
    }
}
