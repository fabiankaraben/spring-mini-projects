package com.example.filestorage.service;

import com.example.filestorage.domain.FileMetadata;
import com.example.filestorage.domain.UploadResult;
import com.example.filestorage.exception.FileNotFoundException;
import com.example.filestorage.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for all file storage operations against the S3-compatible backend.
 *
 * <p>This class contains the core business logic:
 * <ul>
 *   <li>Generating unique object keys using UUIDs to avoid collisions.</li>
 *   <li>Creating the bucket on startup if it does not exist yet.</li>
 *   <li>Translating SDK exceptions into domain exceptions ({@link FileNotFoundException},
 *       {@link StorageException}) so the REST layer stays clean.</li>
 * </ul>
 *
 * <p>It depends only on {@link S3Client} (injected by Spring) and is therefore
 * straightforward to unit-test by mocking that client.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** The AWS S3 / MinIO client configured in {@code S3Config}. */
    private final S3Client s3Client;

    /** The bucket name read from {@code application.yml}. */
    private final String bucketName;

    /**
     * @param s3Client   the configured S3 client bean
     * @param bucketName the name of the bucket to use, from {@code app.s3.bucket-name}
     */
    public FileStorageService(S3Client s3Client,
                              @Value("${app.s3.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    // -------------------------------------------------------------------------
    // Bucket management
    // -------------------------------------------------------------------------

    /**
     * Ensures the configured bucket exists, creating it if necessary.
     *
     * <p>Called explicitly in tests and optionally via an {@code @PostConstruct}
     * in the controller advice. Idempotent – safe to call multiple times.
     */
    public void ensureBucketExists() {
        try {
            // HeadBucket throws NoSuchBucketException when the bucket is missing
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("Bucket '{}' already exists", bucketName);
        } catch (NoSuchBucketException e) {
            // Bucket does not exist – create it
            log.info("Bucket '{}' not found, creating it", bucketName);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            log.info("Bucket '{}' created successfully", bucketName);
        } catch (S3Exception e) {
            throw new StorageException("Failed to ensure bucket exists: " + bucketName, e);
        }
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    /**
     * Uploads a file received as a Spring {@link MultipartFile} to S3.
     *
     * <p>A UUID-based key is generated to guarantee uniqueness even when multiple
     * users upload files with the same original filename. The original filename is
     * preserved as a suffix so that it remains human-readable.
     *
     * <p>Key format: {@code <UUID>-<originalFilename>}
     * Example:       {@code 3f2504e0-4f89-11d3-9a0c-0305e82c3301-photo.jpg}
     *
     * @param file the multipart file received from the HTTP request
     * @return an {@link UploadResult} containing the bucket name, generated key, and ETag
     * @throws StorageException if the upload fails due to an I/O or SDK error
     */
    public UploadResult uploadFile(MultipartFile file) {
        // Build a unique object key: UUID + original filename
        String key = UUID.randomUUID() + "-" + file.getOriginalFilename();
        String contentType = file.getContentType() != null
                ? file.getContentType()
                : "application/octet-stream";

        log.info("Uploading file '{}' as key '{}' to bucket '{}'", file.getOriginalFilename(), key, bucketName);

        try (InputStream inputStream = file.getInputStream()) {
            // Build the PutObject request with metadata
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            // RequestBody.fromInputStream wraps the stream with its exact length
            PutObjectResponse response = s3Client.putObject(putRequest,
                    RequestBody.fromInputStream(inputStream, file.getSize()));

            log.info("File uploaded successfully: key='{}', etag='{}'", key, response.eTag());
            return new UploadResult(bucketName, key, response.eTag());

        } catch (IOException e) {
            throw new StorageException("Failed to read uploaded file: " + file.getOriginalFilename(), e);
        } catch (S3Exception e) {
            throw new StorageException("Failed to upload file to S3: " + key, e);
        }
    }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    /**
     * Downloads the content of a file by its object key.
     *
     * <p>The returned {@link InputStream} is backed by the live HTTP connection to
     * S3 / MinIO. The caller (controller) must close it after streaming the response.
     *
     * @param key the object key in the bucket
     * @return an open {@link InputStream} with the file content
     * @throws FileNotFoundException if no object with that key exists in the bucket
     * @throws StorageException      if the download fails for another reason
     */
    public InputStream downloadFile(String key) {
        log.info("Downloading file with key '{}' from bucket '{}'", key, bucketName);
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            // ResponseInputStream<GetObjectResponse> implements InputStream
            ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest);
            log.info("File download started for key '{}'", key);
            return s3Stream;

        } catch (NoSuchKeyException e) {
            // The object does not exist – translate to our domain exception
            throw new FileNotFoundException(key);
        } catch (S3Exception e) {
            throw new StorageException("Failed to download file from S3: " + key, e);
        }
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    /**
     * Lists all objects in the configured bucket.
     *
     * <p>Returns a flat list of {@link FileMetadata} records. For very large buckets
     * you would paginate using {@code ListObjectsV2Request.builder().continuationToken(...)},
     * but for this educational project a single page (up to 1000 objects) is sufficient.
     *
     * @return a list of metadata for every object in the bucket (may be empty)
     * @throws StorageException if the listing operation fails
     */
    public List<FileMetadata> listFiles() {
        log.info("Listing files in bucket '{}'", bucketName);
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            // Map each S3Object (SDK model) to our domain FileMetadata record
            List<FileMetadata> files = listResponse.contents().stream()
                    .map(obj -> new FileMetadata(
                            obj.key(),
                            obj.size(),
                            // S3 ListObjects does not return Content-Type; use a placeholder
                            "application/octet-stream",
                            obj.lastModified(),
                            obj.eTag()
                    ))
                    .toList();

            log.info("Found {} files in bucket '{}'", files.size(), bucketName);
            return files;

        } catch (S3Exception e) {
            throw new StorageException("Failed to list files in bucket: " + bucketName, e);
        }
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Deletes the object identified by {@code key} from the bucket.
     *
     * <p>Note: S3 and MinIO do <em>not</em> throw an error if the key does not exist —
     * the delete is idempotent. If "not found" semantics are needed, call
     * {@link #getFileMetadata(String)} first.
     *
     * @param key the object key to delete
     * @throws StorageException if the delete operation fails
     */
    public void deleteFile(String key) {
        log.info("Deleting file with key '{}' from bucket '{}'", key, bucketName);
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("File deleted successfully: key='{}'", key);

        } catch (S3Exception e) {
            throw new StorageException("Failed to delete file from S3: " + key, e);
        }
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    /**
     * Retrieves metadata for a single object without downloading its content.
     *
     * <p>Uses the HeadObject S3 API which returns only headers (Content-Type, size,
     * ETag, Last-Modified) without transferring the file body.
     *
     * @param key the object key to inspect
     * @return a {@link FileMetadata} record with the object's metadata
     * @throws FileNotFoundException if no object with that key exists in the bucket
     * @throws StorageException      if the head request fails for another reason
     */
    public FileMetadata getFileMetadata(String key) {
        log.info("Fetching metadata for key '{}' in bucket '{}'", key, bucketName);
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            HeadObjectResponse headResponse = s3Client.headObject(headRequest);

            return new FileMetadata(
                    key,
                    headResponse.contentLength(),
                    headResponse.contentType(),
                    headResponse.lastModified(),
                    headResponse.eTag()
            );

        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException(key);
        } catch (S3Exception e) {
            throw new StorageException("Failed to fetch metadata for file: " + key, e);
        }
    }
}
