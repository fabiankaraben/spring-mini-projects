package com.example.filestorage.domain;

/**
 * Immutable value object returned after a successful file upload.
 *
 * <p>Contains enough information for the API caller to:
 * <ul>
 *   <li>Identify the uploaded object by its key.</li>
 *   <li>Verify the upload integrity via the ETag (MD5 hash for non-multipart uploads).</li>
 *   <li>Know which bucket the object was stored in.</li>
 * </ul>
 *
 * @param bucket the name of the S3 bucket where the file was stored
 * @param key    the object key (path) assigned to the uploaded file
 * @param etag   the entity tag returned by S3 / MinIO (integrity checksum)
 */
public record UploadResult(
        String bucket,
        String key,
        String etag
) {
}
