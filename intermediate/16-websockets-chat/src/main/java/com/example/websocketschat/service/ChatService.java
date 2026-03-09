package com.example.websocketschat.service;

import com.example.websocketschat.domain.ChatMessage;
import com.example.websocketschat.dto.SendMessageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Domain service responsible for chat business logic.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Build a {@link ChatMessage} from an incoming {@link SendMessageRequest},
 *       stamping it with a server-side timestamp and the CHAT type.</li>
 *   <li>Maintain an in-memory history of recent messages so late-joining
 *       clients can retrieve them via the REST endpoint.</li>
 *   <li>Create system JOIN / LEAVE event messages.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>The history list uses {@link CopyOnWriteArrayList}, which is safe for
 * concurrent reads and occasional writes without external synchronisation.
 * This is appropriate here because reads (history retrieval) dominate writes
 * (one write per message). For very high-throughput scenarios you would switch
 * to a bounded queue backed by Redis or a proper message broker.
 *
 * <h2>Why keep business logic in the service layer?</h2>
 * <p>Keeping the "build a ChatMessage" logic here rather than inline in the
 * controller makes it easy to unit-test without starting a Spring context or
 * mocking the WebSocket infrastructure.
 */
@Service
public class ChatService {

    /**
     * Maximum number of messages retained in memory.
     *
     * <p>Older messages are evicted when the limit is reached so the
     * server's heap does not grow without bound.
     */
    public static final int MAX_HISTORY_SIZE = 100;

    /**
     * In-memory circular buffer of recent chat messages.
     *
     * <p>{@link CopyOnWriteArrayList} is used so that reads (e.g., a newly
     * connected client fetching history) never block concurrent writes.
     */
    private final List<ChatMessage> messageHistory = new CopyOnWriteArrayList<>();

    // ── Message building ──────────────────────────────────────────────────────

    /**
     * Converts an incoming {@link SendMessageRequest} into a fully populated
     * {@link ChatMessage} and stores it in the history.
     *
     * <p>The server assigns the timestamp and type so that clients cannot
     * spoof those values.
     *
     * @param request the validated inbound DTO
     * @return a ready-to-broadcast {@link ChatMessage}
     */
    public ChatMessage buildAndStoreMessage(SendMessageRequest request) {
        // Create the domain message with server-assigned metadata
        ChatMessage message = new ChatMessage();
        message.setSender(request.getSender());
        message.setContent(request.getContent());
        message.setType(ChatMessage.MessageType.CHAT);
        message.setTimestamp(Instant.now()); // server controls the timestamp

        // Persist to in-memory history, evicting oldest if at capacity
        storeMessage(message);
        return message;
    }

    /**
     * Creates a JOIN system message (e.g., "Alice joined the chat").
     *
     * <p>System messages do not go through the DTO because they are
     * generated internally by the server when a user connects.
     *
     * @param username the display name of the joining user
     * @return a ready-to-broadcast JOIN {@link ChatMessage}
     */
    public ChatMessage buildJoinMessage(String username) {
        ChatMessage message = new ChatMessage(
                username,
                username + " joined the chat",
                ChatMessage.MessageType.JOIN
        );
        storeMessage(message);
        return message;
    }

    /**
     * Creates a LEAVE system message (e.g., "Alice left the chat").
     *
     * @param username the display name of the departing user
     * @return a ready-to-broadcast LEAVE {@link ChatMessage}
     */
    public ChatMessage buildLeaveMessage(String username) {
        ChatMessage message = new ChatMessage(
                username,
                username + " left the chat",
                ChatMessage.MessageType.LEAVE
        );
        storeMessage(message);
        return message;
    }

    // ── History ───────────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable snapshot of the current message history.
     *
     * <p>Returns an unmodifiable view so callers cannot accidentally mutate
     * the internal list.
     *
     * @return list of recent {@link ChatMessage} objects, oldest first
     */
    public List<ChatMessage> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(messageHistory));
    }

    /**
     * Returns the number of messages currently in the history.
     *
     * @return history size (between 0 and {@link #MAX_HISTORY_SIZE})
     */
    public int getHistorySize() {
        return messageHistory.size();
    }

    /**
     * Clears all messages from the in-memory history.
     *
     * <p>Mainly useful for tests that need a clean state between runs.
     */
    public void clearHistory() {
        messageHistory.clear();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Adds a message to the history, evicting the oldest entry if the
     * {@link #MAX_HISTORY_SIZE} limit has been reached.
     *
     * @param message the message to store
     */
    private void storeMessage(ChatMessage message) {
        if (messageHistory.size() >= MAX_HISTORY_SIZE) {
            // Remove the oldest message (index 0 = oldest in insertion order)
            messageHistory.remove(0);
        }
        messageHistory.add(message);
    }
}
