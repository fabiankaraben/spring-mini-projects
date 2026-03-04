package com.example.fileupload.controller;

import com.example.fileupload.exception.StorageException;
import com.example.fileupload.service.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Controller to handle file upload incoming requests.
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Endpoint to upload a file.
     * 
     * @param file The file part from a multipart/form-data request.
     * @return A response entity containing a success or error message.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> handleFileUpload(@RequestParam("file") MultipartFile file) {
        try {
            // Save the file delegates entirely to the service.
            String filename = fileStorageService.store(file);
            return ResponseEntity.ok(Map.of("message", "You successfully uploaded " + filename + "!"));
        } catch (StorageException e) {
            // If the storage service fails (e.g. empty file, path issues), we return
            // bounded message and HTTP 400.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    // Global Exception Handler for any untracked or generic exceptions if needed,
    // but not required strictly here.
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, String>> handleStorageException(StorageException exc) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", exc.getMessage()));
    }
}
