package com.example.stompwebsockets.controller;

import com.example.stompwebsockets.domain.PrivateMessage;
import com.example.stompwebsockets.dto.PrivateMessageRequest;
import com.example.stompwebsockets.service.PrivateMessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

/**
 * STOMP WebSocket controller that handles private (direct) message delivery.
 *
 * <h2>Private vs. broadcast messaging</h2>
 * <p>Unlike topic messages which go to every subscriber of a channel, private
 * messages are addressed to a specific user. Spring's user-destination feature
 * routes the message only to STOMP sessions whose Principal name matches the
 * intended recipient.
 *
 * <h2>Message flow</h2>
 * <pre>
 *   Sender client
 *     └── STOMP SEND /app/private
 *           │  payload: { sender:"alice", recipient:"bob", content:"Hey!" }
 *           ▼
 *   PrivateMessageController.sendPrivate()
 *           │
 *           ▼
 *   PrivateMessageService.dispatch()
 *           │  messagingTemplate.convertAndSendToUser("bob", "/queue/private", message)
 *           ▼
 *   In-memory STOMP broker
 *           │  routes to /user/bob/queue/private
 *           ▼
 *   Bob's client (subscribed to /user/queue/private)
 * </pre>
 *
 * <p>Alice's client does NOT receive the message (no {@code @SendTo} annotation
 * is used here). Only Bob receives it via the user-destination mechanism.
 */
@Controller
public class PrivateMessageController {

    /** Service responsible for building and dispatching private messages. */
    private final PrivateMessageService privateMessageService;

    /**
     * Constructor injection of the private message service.
     *
     * @param privateMessageService the service that routes private messages
     */
    public PrivateMessageController(PrivateMessageService privateMessageService) {
        this.privateMessageService = privateMessageService;
    }

    /**
     * Handles a STOMP SEND to {@code /app/private}.
     *
     * <p>Workflow:
     * <ol>
     *   <li>Receives the validated {@link PrivateMessageRequest} payload.</li>
     *   <li>Delegates to {@link PrivateMessageService#dispatch} which builds
     *       the message and calls {@code convertAndSendToUser} to route it
     *       exclusively to the recipient's STOMP session.</li>
     *   <li>Returns {@code void} – there is no {@code @SendTo} here because
     *       the service already performs the targeted delivery. Returning void
     *       means Spring will NOT broadcast anything back to the sender either.</li>
     * </ol>
     *
     * @param request the incoming private message payload (validated by Spring)
     */
    @MessageMapping("/private")   // Handles STOMP SEND to /app/private
    public void sendPrivate(@Validated PrivateMessageRequest request) {
        // Delegate all routing logic to the service layer
        privateMessageService.dispatch(request);
    }
}
