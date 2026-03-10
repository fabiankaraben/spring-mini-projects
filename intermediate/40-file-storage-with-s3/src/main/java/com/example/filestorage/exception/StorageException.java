package com.example.filestorage.exception;

/**
 * Domain exception thrown when a storage operation fails due to an infrastructure error.
 *
 * <p>Examples:
 * <ul>
 *   <li>Unable to connect to MinIO / S3.</li>
 *   <li>Permission denied when writing to a bucket.</li>
 *   <li>Bucket does not exist and cannot be created.</li>
 * </ul>
 *
 * <p>This exception is caught by {@link GlobalExceptionHandler} and translated
 * into an HTTP 500 Internal Server Error response.
 */
public class StorageException extends RuntimeException {

    /**
     * @param message a human-readable description of the storage failure
     * @param cause   the underlying SDK or I/O exception
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
