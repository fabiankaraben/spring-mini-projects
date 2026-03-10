package com.example.filestorage.controller;

import com.example.filestorage.domain.FileMetadata;
import com.example.filestorage.domain.UploadResult;
import com.example.filestorage.service.FileStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * REST controller that exposes the file storage API.
 *
 * <p>Base path: {@code /api/files}
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST   /api/files/upload}         – Upload a file (multipart/form-data)</li>
 *   <li>{@code GET    /api/files/{key}/download}  – Download a file by its object key</li>
 *   <li>{@code GET    /api/files}                 – List all files in the bucket</li>
 *   <li>{@code GET    /api/files/{key}/metadata}  – Get metadata for a single file</li>
 *   <li>{@code DELETE /api/files/{key}}           – Delete a file by its object key</li>
 * </ul>
 *
 * <p>The controller is intentionally thin: all business logic lives in
 * {@link FileStorageService}. The controller only maps HTTP semantics
 * (status codes, headers, response bodies) to/from service method results.
 */
@RestController
@RequestMapping("/api/files")
public class FileStorageController {

    private final FileStorageService fileStorageService;

    /**
     * Constructor injection – preferred over field injection for testability.
     *
     * @param fileStorageService the service that handles all S3 operations
     */
    public FileStorageController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    /**
     * Uploads a file to the S3 bucket.
     *
     * <p>Expects a {@code multipart/form-data} request with a {@code file} field.
     *
     * <p>Example with curl:
     * <pre>
     * curl -X POST http://localhost:8080/api/files/upload \
     *      -F "file=@/path/to/photo.jpg"
     * </pre>
     *
     * @param file the multipart file part received from the client
     * @return HTTP 200 with an {@link UploadResult} JSON body containing the assigned key
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResult> uploadFile(@RequestParam("file") MultipartFile file) {
        UploadResult result = fileStorageService.uploadFile(file);
        // Return 200 OK with the upload result (bucket, key, etag)
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    /**
     * Downloads a file from the S3 bucket by its object key.
     *
     * <p>The object key must be URL-encoded if it contains slashes or special characters.
     * Spring's {@code @PathVariable} uses {@code /{key:.+}} to capture dots in filenames.
     *
     * <p>Example with curl:
     * <pre>
     * curl -O -J "http://localhost:8080/api/files/3f2504e0-photo.jpg/download"
     * </pre>
     *
     * @param key the object key of the file to download
     * @return HTTP 200 with the file content as an octet-stream, or 404 if not found
     */
    @GetMapping("/{key:.+}/download")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String key) {
        InputStream fileStream = fileStorageService.downloadFile(key);

        // Wrap the S3 InputStream in an InputStreamResource so Spring can stream it
        return ResponseEntity.ok()
                // Force download with the original key as the suggested filename
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(fileStream));
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    /**
     * Lists all files stored in the S3 bucket.
     *
     * <p>Example with curl:
     * <pre>
     * curl http://localhost:8080/api/files
     * </pre>
     *
     * @return HTTP 200 with a JSON array of {@link FileMetadata} objects
     */
    @GetMapping
    public ResponseEntity<List<FileMetadata>> listFiles() {
        return ResponseEntity.ok(fileStorageService.listFiles());
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    /**
     * Returns metadata for a single file without downloading its content.
     *
     * <p>Example with curl:
     * <pre>
     * curl http://localhost:8080/api/files/3f2504e0-photo.jpg/metadata
     * </pre>
     *
     * @param key the object key to inspect
     * @return HTTP 200 with a {@link FileMetadata} JSON body, or 404 if not found
     */
    @GetMapping("/{key:.+}/metadata")
    public ResponseEntity<FileMetadata> getFileMetadata(@PathVariable String key) {
        return ResponseEntity.ok(fileStorageService.getFileMetadata(key));
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Deletes a file from the S3 bucket.
     *
     * <p>Example with curl:
     * <pre>
     * curl -X DELETE http://localhost:8080/api/files/3f2504e0-photo.jpg
     * </pre>
     *
     * @param key the object key to delete
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping("/{key:.+}")
    public ResponseEntity<Void> deleteFile(@PathVariable String key) {
        fileStorageService.deleteFile(key);
        // 204 No Content is the conventional response for a successful DELETE
        return ResponseEntity.noContent().build();
    }
}
