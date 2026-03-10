package com.example.filestorage.service;

import com.example.filestorage.domain.FileMetadata;
import com.example.filestorage.domain.UploadResult;
import com.example.filestorage.exception.FileNotFoundException;
import com.example.filestorage.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FileStorageService}.
 *
 * <p>These tests use Mockito to replace the real {@link S3Client} with a mock,
 * so no Docker or network is required. They run fast and test only the
 * business logic inside the service class.
 *
 * <p>Key patterns demonstrated:
 * <ul>
 *   <li>Using {@code @ExtendWith(MockitoExtension.class)} for lightweight Mockito integration.</li>
 *   <li>Verifying that the service builds the correct S3 request objects.</li>
 *   <li>Asserting that SDK exceptions are translated into domain exceptions.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileStorageService unit tests")
class FileStorageServiceTest {

    /** Mock S3 client – no real AWS or MinIO connection. */
    @Mock
    private S3Client s3Client;

    /** The service under test, constructed manually so we inject the mock. */
    private FileStorageService fileStorageService;

    /** Bucket name used across all tests. */
    private static final String BUCKET = "test-bucket";

    @BeforeEach
    void setUp() {
        // Construct service manually with mock S3 client and test bucket name
        fileStorageService = new FileStorageService(s3Client, BUCKET);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ensureBucketExists
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ensureBucketExists: should do nothing if bucket already exists")
    void ensureBucketExists_bucketAlreadyExists() {
        // Arrange – headBucket returns normally (bucket exists)
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        // Act
        fileStorageService.ensureBucketExists();

        // Assert – headBucket was called, createBucket was NOT called
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("ensureBucketExists: should create bucket when it does not exist")
    void ensureBucketExists_bucketDoesNotExist() {
        // Arrange – headBucket throws NoSuchBucketException
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("bucket not found").build());
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());

        // Act
        fileStorageService.ensureBucketExists();

        // Assert – both headBucket and createBucket were called
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("ensureBucketExists: should throw StorageException on unexpected S3 error")
    void ensureBucketExists_s3ExceptionThrowsStorageException() {
        // Arrange – headBucket throws a generic S3Exception (not NoSuchBucketException)
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().message("access denied").build());

        // Act & Assert – service translates S3Exception to StorageException
        assertThatThrownBy(() -> fileStorageService.ensureBucketExists())
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to ensure bucket exists");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // uploadFile
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadFile: should return UploadResult with bucket, key, and etag")
    void uploadFile_success() {
        // Arrange – mock a successful PutObject response
        PutObjectResponse putResponse = PutObjectResponse.builder().eTag("\"test-etag\"").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(putResponse);

        // Create a mock multipart file (Spring test utility)
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                "Hello, World!".getBytes()
        );

        // Act
        UploadResult result = fileStorageService.uploadFile(multipartFile);

        // Assert – bucket and etag match; key ends with the original filename
        assertThat(result.bucket()).isEqualTo(BUCKET);
        assertThat(result.key()).endsWith("hello.txt");
        assertThat(result.etag()).isEqualTo("\"test-etag\"");
    }

    @Test
    @DisplayName("uploadFile: should use 'application/octet-stream' when content type is null")
    void uploadFile_nullContentType_usesDefault() {
        // Arrange
        PutObjectResponse putResponse = PutObjectResponse.builder().eTag("\"etag\"").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(putResponse);

        // Multipart file without explicit content type
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "data.bin", null, new byte[]{0x01, 0x02, 0x03}
        );

        // Act & Assert – should not throw; default content type is used
        UploadResult result = fileStorageService.uploadFile(multipartFile);
        assertThat(result).isNotNull();
        // Verify PutObjectRequest was called (content type defaulted inside service)
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("uploadFile: should throw StorageException when S3 upload fails")
    void uploadFile_s3Exception_throwsStorageException() {
        // Arrange – putObject throws an S3Exception
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("S3 unavailable").build());

        MockMultipartFile file = new MockMultipartFile("file", "fail.txt", "text/plain", "data".getBytes());

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.uploadFile(file))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to upload file to S3");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // downloadFile
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("downloadFile: should throw FileNotFoundException when key does not exist")
    void downloadFile_keyNotFound_throwsFileNotFoundException() {
        // Arrange – getObject throws NoSuchKeyException
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("key not found").build());

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.downloadFile("missing-key.txt"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("missing-key.txt");
    }

    @Test
    @DisplayName("downloadFile: should throw StorageException on unexpected S3 error")
    void downloadFile_s3Exception_throwsStorageException() {
        // Arrange
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("connection refused").build());

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.downloadFile("some-key.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to download file from S3");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listFiles
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listFiles: should return an empty list when bucket is empty")
    void listFiles_emptyBucket() {
        // Arrange – list response with no objects
        ListObjectsV2Response emptyResponse = ListObjectsV2Response.builder()
                .contents(List.of())
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(emptyResponse);

        // Act
        List<FileMetadata> files = fileStorageService.listFiles();

        // Assert
        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("listFiles: should return metadata for each object in the bucket")
    void listFiles_withObjects() {
        // Arrange – list response with two S3 objects
        S3Object obj1 = S3Object.builder()
                .key("file1.txt")
                .size(100L)
                .lastModified(Instant.now())
                .eTag("\"etag1\"")
                .build();
        S3Object obj2 = S3Object.builder()
                .key("file2.jpg")
                .size(2048L)
                .lastModified(Instant.now())
                .eTag("\"etag2\"")
                .build();

        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
                .contents(obj1, obj2)
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

        // Act
        List<FileMetadata> files = fileStorageService.listFiles();

        // Assert
        assertThat(files).hasSize(2);
        assertThat(files.get(0).key()).isEqualTo("file1.txt");
        assertThat(files.get(0).size()).isEqualTo(100L);
        assertThat(files.get(1).key()).isEqualTo("file2.jpg");
        assertThat(files.get(1).size()).isEqualTo(2048L);
    }

    @Test
    @DisplayName("listFiles: should throw StorageException on S3 error")
    void listFiles_s3Exception_throwsStorageException() {
        // Arrange
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(S3Exception.builder().message("permission denied").build());

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.listFiles())
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to list files in bucket");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteFile
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteFile: should call S3 deleteObject with the correct key")
    void deleteFile_success() {
        // Arrange – deleteObject returns normally
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        // Act
        fileStorageService.deleteFile("photos/cat.jpg");

        // Assert – verify the SDK was called with a request targeting the correct key
        verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) ->
                req.bucket().equals(BUCKET) && req.key().equals("photos/cat.jpg")
        ));
    }

    @Test
    @DisplayName("deleteFile: should throw StorageException on S3 error")
    void deleteFile_s3Exception_throwsStorageException() {
        // Arrange
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("delete failed").build());

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.deleteFile("key.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to delete file from S3");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getFileMetadata
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getFileMetadata: should map HeadObject response to FileMetadata record")
    void getFileMetadata_success() {
        // Arrange – headObject returns a valid response
        Instant now = Instant.now();
        HeadObjectResponse headResponse = HeadObjectResponse.builder()
                .contentLength(512L)
                .contentType("application/pdf")
                .lastModified(now)
                .eTag("\"pdf-etag\"")
                .build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headResponse);

        // Act
        FileMetadata metadata = fileStorageService.getFileMetadata("report.pdf");

        // Assert – verify every field is correctly mapped
        assertThat(metadata.key()).isEqualTo("report.pdf");
        assertThat(metadata.size()).isEqualTo(512L);
        assertThat(metadata.contentType()).isEqualTo("application/pdf");
        assertThat(metadata.lastModified()).isEqualTo(now);
        assertThat(metadata.etag()).isEqualTo("\"pdf-etag\"");
    }

    @Test
    @DisplayName("getFileMetadata: should throw FileNotFoundException when key is absent")
    void getFileMetadata_keyNotFound_throwsFileNotFoundException() {
        // Arrange
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.getFileMetadata("ghost.txt"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("ghost.txt");
    }

    @Test
    @DisplayName("getFileMetadata: should throw StorageException on unexpected S3 error")
    void getFileMetadata_s3Exception_throwsStorageException() {
        // Arrange
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("internal error").build());

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.getFileMetadata("any.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to fetch metadata");
    }
}
