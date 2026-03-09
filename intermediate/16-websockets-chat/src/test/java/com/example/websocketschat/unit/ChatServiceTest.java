package com.example.websocketschat.unit;

import com.example.websocketschat.domain.ChatMessage;
import com.example.websocketschat.dto.SendMessageRequest;
import com.example.websocketschat.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatService}.
 *
 * <p>These tests verify the core domain logic – message building, history
 * management, and eviction – in complete isolation without starting a Spring
 * context, loading any configuration, or requiring Docker/Testcontainers.
 *
 * <p>We use:
 * <ul>
 *   <li><strong>JUnit 5</strong> ({@code @Test}, {@code @BeforeEach},
 *       {@code @DisplayName}) for test structure and lifecycle.</li>
 *   <li><strong>AssertJ</strong> ({@code assertThat}) for fluent, readable
 *       assertions.</li>
 *   <li><strong>Mockito Extension</strong> ({@code @ExtendWith(MockitoExtension.class)})
 *       for consistency with the test suite, even though no mocks are needed
 *       here since {@link ChatService} has no dependencies.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService – Unit Tests")
class ChatServiceTest {

    /**
     * The class under test. Instantiated manually (not via Spring) so the test
     * runs without an application context.
     */
    private ChatService chatService;

    /**
     * Creates a fresh {@link ChatService} before each test to ensure tests
     * do not share in-memory history state.
     */
    @BeforeEach
    void setUp() {
        chatService = new ChatService();
    }

    // ── buildAndStoreMessage ──────────────────────────────────────────────────

    @Test
    @DisplayName("buildAndStoreMessage should return a ChatMessage with CHAT type")
    void buildAndStoreMessage_shouldReturnChatType() {
        SendMessageRequest request = new SendMessageRequest("Alice", "Hello!");

        ChatMessage result = chatService.buildAndStoreMessage(request);

        assertThat(result.getType()).isEqualTo(ChatMessage.MessageType.CHAT);
    }

    @Test
    @DisplayName("buildAndStoreMessage should copy sender and content from request")
    void buildAndStoreMessage_shouldCopySenderAndContent() {
        SendMessageRequest request = new SendMessageRequest("Alice", "Hello everyone!");

        ChatMessage result = chatService.buildAndStoreMessage(request);

        assertThat(result.getSender()).isEqualTo("Alice");
        assertThat(result.getContent()).isEqualTo("Hello everyone!");
    }

    @Test
    @DisplayName("buildAndStoreMessage should assign a server-side timestamp")
    void buildAndStoreMessage_shouldAssignTimestamp() {
        Instant before = Instant.now();
        ChatMessage result = chatService.buildAndStoreMessage(new SendMessageRequest("Alice", "Hi"));
        Instant after = Instant.now();

        // Timestamp must be assigned by the server (not from the client request)
        assertThat(result.getTimestamp()).isNotNull();
        assertThat(result.getTimestamp()).isAfterOrEqualTo(before);
        assertThat(result.getTimestamp()).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("buildAndStoreMessage should add the message to history")
    void buildAndStoreMessage_shouldAddToHistory() {
        chatService.buildAndStoreMessage(new SendMessageRequest("Alice", "Hello!"));

        assertThat(chatService.getHistorySize()).isEqualTo(1);
    }

    @Test
    @DisplayName("buildAndStoreMessage should add multiple messages to history in order")
    void buildAndStoreMessage_shouldMaintainInsertionOrder() {
        chatService.buildAndStoreMessage(new SendMessageRequest("Alice", "First message"));
        chatService.buildAndStoreMessage(new SendMessageRequest("Bob", "Second message"));
        chatService.buildAndStoreMessage(new SendMessageRequest("Charlie", "Third message"));

        List<ChatMessage> history = chatService.getHistory();

        // History must preserve insertion order (oldest first)
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getSender()).isEqualTo("Alice");
        assertThat(history.get(1).getSender()).isEqualTo("Bob");
        assertThat(history.get(2).getSender()).isEqualTo("Charlie");
    }

    // ── buildJoinMessage ──────────────────────────────────────────────────────

    @Test
    @DisplayName("buildJoinMessage should return a message with JOIN type")
    void buildJoinMessage_shouldReturnJoinType() {
        ChatMessage result = chatService.buildJoinMessage("Alice");

        assertThat(result.getType()).isEqualTo(ChatMessage.MessageType.JOIN);
    }

    @Test
    @DisplayName("buildJoinMessage should set sender to the given username")
    void buildJoinMessage_shouldSetSender() {
        ChatMessage result = chatService.buildJoinMessage("Alice");

        assertThat(result.getSender()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("buildJoinMessage should include the username in the content")
    void buildJoinMessage_shouldIncludeUsernameInContent() {
        ChatMessage result = chatService.buildJoinMessage("Alice");

        // Content should mention the user's name so clients can display it
        assertThat(result.getContent()).contains("Alice");
    }

    @Test
    @DisplayName("buildJoinMessage should assign a non-null timestamp")
    void buildJoinMessage_shouldAssignTimestamp() {
        ChatMessage result = chatService.buildJoinMessage("Alice");

        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("buildJoinMessage should add the message to history")
    void buildJoinMessage_shouldAddToHistory() {
        chatService.buildJoinMessage("Alice");

        assertThat(chatService.getHistorySize()).isEqualTo(1);
    }

    // ── buildLeaveMessage ─────────────────────────────────────────────────────

    @Test
    @DisplayName("buildLeaveMessage should return a message with LEAVE type")
    void buildLeaveMessage_shouldReturnLeaveType() {
        ChatMessage result = chatService.buildLeaveMessage("Bob");

        assertThat(result.getType()).isEqualTo(ChatMessage.MessageType.LEAVE);
    }

    @Test
    @DisplayName("buildLeaveMessage should set sender to the given username")
    void buildLeaveMessage_shouldSetSender() {
        ChatMessage result = chatService.buildLeaveMessage("Bob");

        assertThat(result.getSender()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("buildLeaveMessage should include the username in the content")
    void buildLeaveMessage_shouldIncludeUsernameInContent() {
        ChatMessage result = chatService.buildLeaveMessage("Bob");

        assertThat(result.getContent()).contains("Bob");
    }

    @Test
    @DisplayName("buildLeaveMessage should add the message to history")
    void buildLeaveMessage_shouldAddToHistory() {
        chatService.buildLeaveMessage("Bob");

        assertThat(chatService.getHistorySize()).isEqualTo(1);
    }

    // ── getHistory ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getHistory should return an empty list when no messages have been sent")
    void getHistory_shouldReturnEmptyListInitially() {
        assertThat(chatService.getHistory()).isEmpty();
    }

    @Test
    @DisplayName("getHistory should return an unmodifiable list")
    void getHistory_shouldReturnUnmodifiableList() {
        chatService.buildAndStoreMessage(new SendMessageRequest("Alice", "Hello"));

        List<ChatMessage> history = chatService.getHistory();

        // Attempt to mutate the returned list must throw UnsupportedOperationException
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> history.add(new ChatMessage())
        );
    }

    @Test
    @DisplayName("getHistory should contain messages of all types in insertion order")
    void getHistory_shouldContainMixedTypes() {
        chatService.buildJoinMessage("Alice");
        chatService.buildAndStoreMessage(new SendMessageRequest("Alice", "Hi!"));
        chatService.buildLeaveMessage("Alice");

        List<ChatMessage> history = chatService.getHistory();

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getType()).isEqualTo(ChatMessage.MessageType.JOIN);
        assertThat(history.get(1).getType()).isEqualTo(ChatMessage.MessageType.CHAT);
        assertThat(history.get(2).getType()).isEqualTo(ChatMessage.MessageType.LEAVE);
    }

    // ── getHistorySize ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getHistorySize should return 0 initially")
    void getHistorySize_shouldReturnZeroInitially() {
        assertThat(chatService.getHistorySize()).isEqualTo(0);
    }

    @Test
    @DisplayName("getHistorySize should increment with each stored message")
    void getHistorySize_shouldIncrementWithEachMessage() {
        chatService.buildAndStoreMessage(new SendMessageRequest("Alice", "1"));
        assertThat(chatService.getHistorySize()).isEqualTo(1);

        chatService.buildAndStoreMessage(new SendMessageRequest("Bob", "2"));
        assertThat(chatService.getHistorySize()).isEqualTo(2);
    }

    // ── clearHistory ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("clearHistory should remove all messages from the history")
    void clearHistory_shouldEmptyTheList() {
        chatService.buildAndStoreMessage(new SendMessageRequest("Alice", "Hello"));
        chatService.buildAndStoreMessage(new SendMessageRequest("Bob", "Hi"));

        chatService.clearHistory();

        assertThat(chatService.getHistorySize()).isEqualTo(0);
        assertThat(chatService.getHistory()).isEmpty();
    }

    // ── History eviction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("History should not exceed MAX_HISTORY_SIZE even after many messages")
    void history_shouldEvictOldestWhenAtCapacity() {
        // Fill history to exactly MAX_HISTORY_SIZE
        for (int i = 0; i < ChatService.MAX_HISTORY_SIZE; i++) {
            chatService.buildAndStoreMessage(new SendMessageRequest("user" + i, "msg" + i));
        }
        assertThat(chatService.getHistorySize()).isEqualTo(ChatService.MAX_HISTORY_SIZE);

        // Adding one more should evict the oldest (msg0 from user0)
        chatService.buildAndStoreMessage(new SendMessageRequest("newuser", "overflow message"));

        assertThat(chatService.getHistorySize()).isEqualTo(ChatService.MAX_HISTORY_SIZE);
        // The oldest message should have been evicted
        assertThat(chatService.getHistory().get(0).getSender()).isEqualTo("user1");
        // The newest message should be at the end
        assertThat(chatService.getHistory().get(ChatService.MAX_HISTORY_SIZE - 1).getSender())
                .isEqualTo("newuser");
    }
}
