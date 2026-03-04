package com.example.eventpublisher.publisher;

import com.example.eventpublisher.event.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRegistrationPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private UserRegistrationPublisher publisher;

    @Test
    void testPublishUserRegistration() {
        // Arrange
        String testUsername = "fabian";

        // Act
        publisher.publishUserRegistration(testUsername);

        // Assert
        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        UserRegisteredEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getUsername()).isEqualTo(testUsername);
    }
}
