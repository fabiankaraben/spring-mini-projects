package com.example.fileupload.service;

import com.example.fileupload.exception.StorageException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Service class responsible for handling file storage operations.
 */
@Service
public class FileStorageService {

    private final Path rootLocation;

    /**
     * Constructor that initializes the storage location from properties.
     *
     * @param location the location to store uploaded files configured in
     *                 application.properties.
     */
    public FileStorageService(@Value("${storage.location:uploads}") String location) {
        this.rootLocation = Paths.get(location);
    }

    /**
     * Initialize the storage directory on application startup or bean creation.
     */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage location", e);
        }
    }

    /**
     * Stores the incoming MultipartFile into the specified storage location.
     *
     * @param file the file to be uploaded.
     * @return the name of the file that was stored.
     * @throws StorageException if the file is empty, invalid, or an IO error
     *                          occurs.
     */
    public String store(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file.");
            }

            // Getting the original filename to save it with the same name.
            String filename = Objects.requireNonNull(file.getOriginalFilename());

            // Prevent path traversal vulnerabilities
            if (filename.contains("..")) {
                throw new StorageException(
                        "Cannot store file with relative path outside current directory " + filename);
            }

            // Resolve the destination path
            Path destinationFile = this.rootLocation.resolve(Paths.get(filename)).normalize().toAbsolutePath();

            // Security check: ensure the file matches our intended directory.
            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                throw new StorageException("Cannot store file outside current directory.");
            }

            // Copying the file stream to the destination.
            // StandardCopyOption.REPLACE_EXISTING handles overwrites.
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return filename;
        } catch (IOException e) {
            throw new StorageException("Failed to store file.", e);
        }
    }
}
