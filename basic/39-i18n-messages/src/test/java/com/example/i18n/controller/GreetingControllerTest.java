package com.example.i18n.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GreetingControllerTest {

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private GreetingController greetingController;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @Test
    void shouldReturnHelloMessage() {
        // Arrange
        String expectedMessage = "Hello World!";
        when(messageSource.getMessage(eq("greeting.hello"), any(), any(Locale.class)))
                .thenReturn(expectedMessage);

        // Act
        String actualMessage = greetingController.hello();

        // Assert
        assertEquals(expectedMessage, actualMessage);
        verify(messageSource).getMessage(eq("greeting.hello"), eq(null), any(Locale.class));
    }

    @Test
    void shouldReturnWelcomeMessage() {
        // Arrange
        String name = "Fabian";
        String expectedMessage = "Welcome to our application, Fabian!";
        when(messageSource.getMessage(eq("greeting.welcome"), any(Object[].class), any(Locale.class)))
                .thenReturn(expectedMessage);

        // Act
        String actualMessage = greetingController.welcome(name);

        // Assert
        assertEquals(expectedMessage, actualMessage);
        verify(messageSource).getMessage(eq("greeting.welcome"), any(Object[].class), any(Locale.class));
    }
}
