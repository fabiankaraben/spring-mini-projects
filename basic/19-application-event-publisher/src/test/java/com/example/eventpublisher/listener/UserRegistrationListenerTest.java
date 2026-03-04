package com.example.eventpublisher.listener;

import com.example.eventpublisher.event.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UserRegistrationListenerTest {

    @InjectMocks
    private UserRegistrationListener listener;

    /**
     * Confirms the handler correctly processes incoming events.
     */
    @Test
    void testHandleUserRegistrationEvent() {
        // Arrange
        String testUsername = "fabian";
        UserRegisteredEvent event = new UserRegisteredEvent(testUsername);

        // Act
        listener.handleUserRegistrationEvent(event);

        // Assert
        List<String> registeredUsernames = listener.getRegisteredUsernames();
        assertThat(registeredUsernames).hasSize(1);
        assertThat(registeredUsernames.get(0)).isEqualTo(testUsername);
    }
}
