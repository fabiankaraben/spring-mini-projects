package com.example.custombanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BannerController using Mockito to mock dependencies.
 * This tests the controller logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
public class BannerControllerTest {

    @Mock
    private BannerService bannerService;

    @InjectMocks
    private BannerController bannerController;

    /**
     * Tests that the home method returns the message from the service.
     * Verifies that the service method is called and the correct response is returned.
     */
    @Test
    void home_shouldReturnMessageFromService() {
        // Arrange
        String expectedMessage = "Welcome to the Custom Startup Banner Application!";
        when(bannerService.getBannerMessage()).thenReturn(expectedMessage);

        // Act
        String result = bannerController.home();

        // Assert
        assertEquals(expectedMessage, result);
        verify(bannerService).getBannerMessage();
    }
}
