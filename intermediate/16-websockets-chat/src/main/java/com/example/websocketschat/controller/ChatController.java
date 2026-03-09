package com.example.websocketschat.controller;

import com.example.websocketschat.domain.ChatMessage;
import com.example.websocketschat.dto.SendMessageRequest;
import com.example.websocketschat.service.ChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

/**
 * STOMP WebSocket controller that handles incoming chat messages.
 *
 * <h2>How STOMP routing works</h2>
 * <p>When a client sends a STOMP SEND frame to destination {@code /app/chat.send},
 * the Spring message dispatcher looks for a {@code @MessageMapping} method whose
 * value (combined with the application destination prefix {@code /app}) matches
 * that destination. It then:
 * <ol>
 *   <li>Deserialises the JSON payload into {@link SendMessageRequest}.</li>
 *   <li>Invokes {@link #handleSend}.</li>
 *   <li>Takes the return value and forwards it to the destination specified in
 *       {@code @SendTo}, i.e., {@code /topic/messages}.</li>
 *   <li>The in-memory broker delivers the message to every client subscribed to
 *       {@code /topic/messages}.</li>
 * </ol>
 *
 * <h2>Difference from @RestController</h2>
 * <p>This class uses plain {@code @Controller} (not {@code @RestController})
 * because message handling is done via STOMP, not HTTP. The return value is
 * serialised by the configured STOMP message converter (Jackson) and published
 * to the broker, not written to an HTTP response body.
 */
@Controller
public class ChatController {

    /** Chat domain service – builds and stores messages. */
    private final ChatService chatService;

    /**
     * Constructor injection of {@link ChatService}.
     *
     * @param chatService the service responsible for building/storing messages
     */
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Handles a user chat message sent to {@code /app/chat.send}.
     *
     * <p>The method:
     * <ol>
     *   <li>Receives the deserialised {@link SendMessageRequest} payload.</li>
     *   <li>Delegates to {@link ChatService#buildAndStoreMessage} to produce a
     *       fully timestamped {@link ChatMessage}.</li>
     *   <li>Returns the {@link ChatMessage}; Spring then broadcasts it to all
     *       subscribers of {@code /topic/messages} via {@code @SendTo}.</li>
     * </ol>
     *
     * @param request the incoming chat message payload (validated by Spring)
     * @return the broadcast {@link ChatMessage} sent to all subscribers
     */
    @MessageMapping("/chat.send")          // Handles STOMP SEND to /app/chat.send
    @SendTo("/topic/messages")             // Broadcasts the return value to all subscribers of this topic
    public ChatMessage handleSend(@Validated SendMessageRequest request) {
        // Delegate to service – it assigns timestamp and stores history
        return chatService.buildAndStoreMessage(request);
    }

    /**
     * Handles a user JOIN event sent to {@code /app/chat.join}.
     *
     * <p>Clients call this after connecting to announce their presence.
     * A system JOIN message is broadcast to all subscribers of
     * {@code /topic/messages} so the UI can show an "Alice joined" notification.
     *
     * @param request payload containing only the {@code sender} field (content is ignored)
     * @return the broadcast JOIN {@link ChatMessage}
     */
    @MessageMapping("/chat.join")          // Handles STOMP SEND to /app/chat.join
    @SendTo("/topic/messages")             // Broadcasts to all subscribers
    public ChatMessage handleJoin(@Validated SendMessageRequest request) {
        return chatService.buildJoinMessage(request.getSender());
    }

    /**
     * Handles a user LEAVE event sent to {@code /app/chat.leave}.
     *
     * <p>Clients call this before disconnecting to notify other participants.
     * A LEAVE message is broadcast to all subscribers.
     *
     * @param request payload containing only the {@code sender} field (content is ignored)
     * @return the broadcast LEAVE {@link ChatMessage}
     */
    @MessageMapping("/chat.leave")         // Handles STOMP SEND to /app/chat.leave
    @SendTo("/topic/messages")             // Broadcasts to all subscribers
    public ChatMessage handleLeave(@Validated SendMessageRequest request) {
        return chatService.buildLeaveMessage(request.getSender());
    }
}
