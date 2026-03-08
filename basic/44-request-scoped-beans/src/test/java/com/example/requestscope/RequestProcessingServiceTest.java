package com.example.requestscope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RequestProcessingServiceTest {

    @Mock
    private RequestData requestData;

    @InjectMocks
    private RequestProcessingService service;

    @Test
    void processData_ShouldAddLogs() {
        // Given
        String input = "test";

        // When
        service.processData(input);

        // Then
        verify(requestData).addLog("Service processing input: " + input);
        verify(requestData).addLog("Service finished processing: TEST");
    }
}
