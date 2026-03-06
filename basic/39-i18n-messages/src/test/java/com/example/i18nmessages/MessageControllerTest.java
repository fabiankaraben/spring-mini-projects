package com.example.i18nmessages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit test for MessageController.
 * This test uses Mockito to mock the MessageSource dependency
 * and verifies that the controller correctly retrieves localized messages.
 */
@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    // Mock the MessageSource dependency
    @Mock
    private MessageSource messageSource;

    // Inject the mock into the controller
    @InjectMocks
    private MessageController messageController;

    /**
     * Test getting a message in English.
     * Verifies that the controller calls MessageSource with the correct parameters
     * and returns the expected English message.
     */
    @Test
    void testGetMessageEnglish() {
        // Arrange: Set up the mock behavior
        String key = "greeting";
        Locale locale = Locale.ENGLISH;
        String expectedMessage = "Hello World";
        when(messageSource.getMessage(key, null, locale)).thenReturn(expectedMessage);

        // Act: Call the controller method
        String actualMessage = messageController.getMessage(key, locale);

        // Assert: Verify the result
        assertEquals(expectedMessage, actualMessage);
    }

    /**
     * Test getting a message in Spanish.
     * Verifies that the controller works with different locales.
     */
    @Test
    void testGetMessageSpanish() {
        // Arrange
        String key = "greeting";
        Locale locale = Locale.forLanguageTag("es");
        String expectedMessage = "Hola Mundo";
        when(messageSource.getMessage(key, null, locale)).thenReturn(expectedMessage);

        // Act
        String actualMessage = messageController.getMessage(key, locale);

        // Assert
        assertEquals(expectedMessage, actualMessage);
    }

    /**
     * Test getting a message with a different key.
     * Ensures the key parameter is correctly passed to MessageSource.
     */
    @Test
    void testGetMessageDifferentKey() {
        // Arrange
        String key = "error.notfound";
        Locale locale = Locale.ENGLISH;
        String expectedMessage = "Message not found";
        when(messageSource.getMessage(key, null, locale)).thenReturn(expectedMessage);

        // Act
        String actualMessage = messageController.getMessage(key, locale);

        // Assert
        assertEquals(expectedMessage, actualMessage);
    }
}
