package com.example.filestorage.exception;

/**
 * Domain exception thrown when a requested file (object key) does not exist in the bucket.
 *
 * <p>This exception is caught by {@link GlobalExceptionHandler} and translated
 * into an HTTP 404 Not Found response, keeping the controller logic clean.
 */
public class FileNotFoundException extends RuntimeException {

    /**
     * @param key the object key that was not found in the bucket
     */
    public FileNotFoundException(String key) {
        super("File not found in bucket: " + key);
    }
}
