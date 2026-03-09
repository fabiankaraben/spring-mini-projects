package com.example.stompwebsockets.service;

import com.example.stompwebsockets.domain.PrivateMessage;
import com.example.stompwebsockets.dto.PrivateMessageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Domain service responsible for dispatching private (direct) messages.
 *
 * <h2>How private messaging works in Spring STOMP</h2>
 * <p>Spring provides a "user destination" concept built on top of the standard
 * STOMP broker. When you call
 * {@link SimpMessageSendingOperations#convertAndSendToUser(String, String, Object)},
 * Spring:
 * <ol>
 *   <li>Looks up all active STOMP sessions whose
 *       {@code java.security.Principal} name matches the {@code username} argument.</li>
 *   <li>Rewrites the destination to {@code /user/{sessionId}/queue/private} for
 *       each matching session.</li>
 *   <li>Hands the rewritten destination to the in-memory broker, which delivers
 *       the message only to those specific sessions.</li>
 * </ol>
 *
 * <p>The client subscribes to {@code /user/queue/private} – Spring automatically
 * expands this to include the user-specific prefix for the current session, so
 * the subscription matches the rewritten destination.
 *
 * <h2>Limitation in this mini-project</h2>
 * <p>Full user-destination routing requires the client to authenticate with a
 * {@code Principal}. Since this project omits authentication for simplicity,
 * the private-message delivery is demonstrated by routing to a destination that
 * the test client explicitly subscribes to ({@code /user/{recipient}/queue/private}).
 * In a production system you would use Spring Security to set up the Principal.
 *
 * <h2>Testability</h2>
 * <p>The {@link SimpMessageSendingOperations} dependency can be mocked in unit
 * tests so the messaging infrastructure does not need to be started.
 */
@Service
public class PrivateMessageService {

    /**
     * Spring's unified messaging template, injected by Spring when it creates
     * the service bean. It abstracts over the STOMP broker and provides a clean
     * API for sending messages programmatically.
     */
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * Constructor injection – preferred over {@code @Autowired} field injection
     * because it makes the dependency explicit and enables unit-test mockability.
     *
     * @param messagingTemplate the STOMP messaging template provided by Spring
     */
    public PrivateMessageService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Builds and dispatches a private message to the intended recipient.
     *
     * <p>The method:
     * <ol>
     *   <li>Constructs a {@link PrivateMessage} with a server-assigned timestamp
     *       so the client cannot forge the delivery time.</li>
     *   <li>Calls {@code convertAndSendToUser} to route the message exclusively
     *       to the recipient's active STOMP session(s).</li>
     * </ol>
     *
     * @param request the validated DTO containing sender, recipient, and content
     * @return the dispatched {@link PrivateMessage} (useful for logging/testing)
     */
    public PrivateMessage dispatch(PrivateMessageRequest request) {
        // Build the domain object with server-assigned metadata
        PrivateMessage message = new PrivateMessage(
                request.getSender(),
                request.getRecipient(),
                request.getContent(),
                Instant.now() // server controls the timestamp
        );

        // Route the message exclusively to the recipient's STOMP session.
        // Spring STOMP rewrites /queue/private to /user/{recipient}/queue/private
        // and the in-memory broker delivers it only to matching sessions.
        messagingTemplate.convertAndSendToUser(
                request.getRecipient(),   // username (Principal name) of the recipient
                "/queue/private",         // destination (broker will add user prefix)
                message                   // the payload, serialised to JSON by Jackson
        );

        return message;
    }
}
