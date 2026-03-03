package com.example.formdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * Unit test for FormController.
 * This test isolates the controller by mocking its dependencies
 * (FormHandlerService).
 */
@ExtendWith(MockitoExtension.class)
class FormControllerUnitTest {

    @Mock
    private FormHandlerService formHandlerService; // Mock the dependent service

    @InjectMocks
    private FormController formController; // Inject the mock into the controller

    @Test
    void testHandleFormSubmission() {
        // 1. Setup mock data and behavior
        UserForm form = new UserForm("john_doe", "john@example.com", "Hello there");
        Map<String, Object> expectedResponse = Map.of("status", "success", "data", form);

        when(formHandlerService.processForm(any(UserForm.class))).thenReturn(expectedResponse);

        // 2. Call the controller method directly
        Map<String, Object> actualResponse = formController.handleFormSubmission(form);

        // 3. Verify the result matches our expectations
        assertEquals(expectedResponse, actualResponse);

        // 4. Verify that the method in the mock was called exactly once
        verify(formHandlerService, times(1)).processForm(form);
    }
}
