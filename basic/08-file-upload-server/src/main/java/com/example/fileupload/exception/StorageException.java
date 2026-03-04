package com.example.fileupload.exception;

/**
 * Custom exception used when there are issues with reading, storing or creating
 * the storage directory.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
