package com.example.websocketschat.controller;

import com.example.websocketschat.domain.ChatMessage;
import com.example.websocketschat.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes the in-memory chat history over HTTP.
 *
 * <h2>Why a REST endpoint for a WebSocket application?</h2>
 * <p>Pure WebSocket connections are stateless once established. A client that
 * joins the chat room <em>after</em> other participants have already sent
 * messages would miss those messages. This endpoint lets clients fetch the
 * recent history as part of their connection handshake.
 *
 * <p>It also serves as a convenient way to test the chat from the command line
 * with {@code curl} without requiring a WebSocket client.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/chat/history} – returns up to
 *       {@link com.example.websocketschat.service.ChatService#MAX_HISTORY_SIZE}
 *       recent messages as JSON.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class HistoryController {

    /** Chat domain service – source of truth for the message history. */
    private final ChatService chatService;

    /**
     * Constructor injection of {@link ChatService}.
     *
     * @param chatService the service that stores in-memory message history
     */
    public HistoryController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Returns the list of recent chat messages.
     *
     * <p>Returns HTTP 200 with a JSON array of {@link ChatMessage} objects,
     * ordered from oldest to newest. The array may be empty if no messages
     * have been sent yet.
     *
     * @return {@code 200 OK} with the message history
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getHistory() {
        // Delegate to the service layer – controller only handles HTTP concerns
        return ResponseEntity.ok(chatService.getHistory());
    }
}
