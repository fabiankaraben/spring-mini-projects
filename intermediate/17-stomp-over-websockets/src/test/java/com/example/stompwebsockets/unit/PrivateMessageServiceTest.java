package com.example.stompwebsockets.unit;

import com.example.stompwebsockets.domain.PrivateMessage;
import com.example.stompwebsockets.dto.PrivateMessageRequest;
import com.example.stompwebsockets.service.PrivateMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PrivateMessageService}.
 *
 * <p>These tests run without a Spring context. The only external dependency –
 * {@link SimpMessageSendingOperations} – is mocked with Mockito so the tests
 * do not require a live STOMP broker.
 *
 * <h2>What is tested here</h2>
 * <ul>
 *   <li>The returned {@link PrivateMessage} contains the correct sender,
 *       recipient, content, and a server-assigned timestamp.</li>
 *   <li>{@code convertAndSendToUser} is invoked with exactly the right
 *       recipient username and destination path.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrivateMessageService – Unit Tests")
class PrivateMessageServiceTest {

    /**
     * Mocked STOMP messaging template.
     *
     * <p>Mockito creates a no-op implementation so no real WebSocket
     * infrastructure is needed. We use {@code verify()} to assert that
     * the service called it correctly.
     */
    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    /** The class under test, constructed with the mocked dependency. */
    private PrivateMessageService privateMessageService;

    /** Re-creates the service with a fresh mock before each test. */
    @BeforeEach
    void setUp() {
        privateMessageService = new PrivateMessageService(messagingTemplate);
    }

    // ── dispatch ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("dispatch should return a PrivateMessage with the correct sender")
    void dispatch_shouldSetSender() {
        PrivateMessageRequest request = new PrivateMessageRequest("alice", "bob", "Hey Bob!");

        PrivateMessage result = privateMessageService.dispatch(request);

        assertThat(result.getSender()).isEqualTo("alice");
    }

    @Test
    @DisplayName("dispatch should return a PrivateMessage with the correct recipient")
    void dispatch_shouldSetRecipient() {
        PrivateMessageRequest request = new PrivateMessageRequest("alice", "bob", "Hey Bob!");

        PrivateMessage result = privateMessageService.dispatch(request);

        assertThat(result.getRecipient()).isEqualTo("bob");
    }

    @Test
    @DisplayName("dispatch should return a PrivateMessage with the correct content")
    void dispatch_shouldSetContent() {
        PrivateMessageRequest request = new PrivateMessageRequest("alice", "bob", "Hey Bob!");

        PrivateMessage result = privateMessageService.dispatch(request);

        assertThat(result.getContent()).isEqualTo("Hey Bob!");
    }

    @Test
    @DisplayName("dispatch should assign a server-side timestamp to the returned message")
    void dispatch_shouldAssignServerTimestamp() {
        Instant before = Instant.now();
        PrivateMessage result = privateMessageService.dispatch(
                new PrivateMessageRequest("alice", "bob", "Hi"));
        Instant after = Instant.now();

        // Timestamp must be non-null and fall within the test execution window
        assertThat(result.getTimestamp()).isNotNull();
        assertThat(result.getTimestamp()).isAfterOrEqualTo(before);
        assertThat(result.getTimestamp()).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("dispatch should call convertAndSendToUser with the recipient username")
    void dispatch_shouldCallConvertAndSendToUserWithRecipient() {
        PrivateMessageRequest request = new PrivateMessageRequest("alice", "bob", "Hey!");

        privateMessageService.dispatch(request);

        // Verify the messaging template was invoked with the correct recipient
        verify(messagingTemplate).convertAndSendToUser(
                eq("bob"),             // recipient username (Spring uses this as the Principal name)
                eq("/queue/private"),  // user-destination path
                any(PrivateMessage.class) // the built message object
        );
    }

    @Test
    @DisplayName("dispatch should send the built PrivateMessage object to the broker")
    void dispatch_shouldSendCorrectMessageObjectToBroker() {
        PrivateMessageRequest request = new PrivateMessageRequest("alice", "bob", "Hello!");

        privateMessageService.dispatch(request);

        // Capture the actual PrivateMessage sent to the broker
        ArgumentCaptor<PrivateMessage> captor = ArgumentCaptor.forClass(PrivateMessage.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("bob"),
                eq("/queue/private"),
                captor.capture()
        );

        PrivateMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getSender()).isEqualTo("alice");
        assertThat(sentMessage.getRecipient()).isEqualTo("bob");
        assertThat(sentMessage.getContent()).isEqualTo("Hello!");
        assertThat(sentMessage.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("dispatch should route to /queue/private destination")
    void dispatch_shouldRouteToPrivateQueueDestination() {
        PrivateMessageRequest request = new PrivateMessageRequest("alice", "charlie", "Hi Charlie!");

        privateMessageService.dispatch(request);

        // Confirm the destination is /queue/private (Spring adds /user/{name} prefix internally)
        verify(messagingTemplate).convertAndSendToUser(
                any(String.class),
                eq("/queue/private"),
                any(PrivateMessage.class)
        );
    }
}
