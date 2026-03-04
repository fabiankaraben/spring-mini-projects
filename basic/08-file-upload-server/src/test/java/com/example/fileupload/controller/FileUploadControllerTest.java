package com.example.fileupload.controller;

import com.example.fileupload.exception.StorageException;
import com.example.fileupload.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller sliced testing focusing solely on the web layer of
 * FileUploadController.
 */
@WebMvcTest(FileUploadController.class)
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Use MockitoBean starting from Spring Boot 3.4
    @MockitoBean
    private FileStorageService fileStorageService;

    @Test
    void shouldUploadFileSuccessfully() throws Exception {
        // Arrange
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Spring Framework".getBytes());

        given(fileStorageService.store(any())).willReturn("test.txt");

        // Act & Assert
        mockMvc.perform(multipart("/api/files/upload").file(multipartFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You successfully uploaded test.txt!"));
    }

    @Test
    void shouldReturnBadRequestWhenFileStorageFails() throws Exception {
        // Arrange
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "empty.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]);

        given(fileStorageService.store(any())).willThrow(new StorageException("Failed to store empty file."));

        // Act & Assert
        mockMvc.perform(multipart("/api/files/upload").file(multipartFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Failed to store empty file."));
    }
}
