package com.example.websocketschat.unit;

import com.example.websocketschat.domain.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatMessage}.
 *
 * <p>These tests verify the domain model's default values, constructor
 * behaviour, and accessor correctness in complete isolation – no Spring
 * context, no database, no Docker container.
 *
 * <p>We use:
 * <ul>
 *   <li><strong>JUnit 5</strong> ({@code @Test}, {@code @DisplayName}) for
 *       test structure and lifecycle.</li>
 *   <li><strong>AssertJ</strong> ({@code assertThat}) for fluent, readable
 *       assertions.</li>
 * </ul>
 */
@DisplayName("ChatMessage – Unit Tests")
class ChatMessageTest {

    // ── No-args constructor ───────────────────────────────────────────────────

    @Test
    @DisplayName("No-args constructor should produce a message with CHAT type by default")
    void noArgsConstructor_shouldDefaultToChatType() {
        ChatMessage message = new ChatMessage();

        // Default type must be CHAT so clients that omit the type field still work
        assertThat(message.getType()).isEqualTo(ChatMessage.MessageType.CHAT);
    }

    @Test
    @DisplayName("No-args constructor should leave sender, content, and timestamp null")
    void noArgsConstructor_shouldLeaveFieldsNull() {
        ChatMessage message = new ChatMessage();

        assertThat(message.getSender()).isNull();
        assertThat(message.getContent()).isNull();
        assertThat(message.getTimestamp()).isNull();
    }

    // ── Convenience constructor ───────────────────────────────────────────────

    @Test
    @DisplayName("Convenience constructor should set all fields correctly")
    void convenienceConstructor_shouldSetAllFields() {
        ChatMessage message = new ChatMessage("Alice", "Alice joined the chat", ChatMessage.MessageType.JOIN);

        assertThat(message.getSender()).isEqualTo("Alice");
        assertThat(message.getContent()).isEqualTo("Alice joined the chat");
        assertThat(message.getType()).isEqualTo(ChatMessage.MessageType.JOIN);
        // Timestamp should be assigned at construction time
        assertThat(message.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Convenience constructor should assign a timestamp close to now")
    void convenienceConstructor_shouldAssignCurrentTimestamp() {
        Instant before = Instant.now();
        ChatMessage message = new ChatMessage("Bob", "Bob left the chat", ChatMessage.MessageType.LEAVE);
        Instant after = Instant.now();

        // The timestamp must fall within the range [before, after]
        assertThat(message.getTimestamp()).isAfterOrEqualTo(before);
        assertThat(message.getTimestamp()).isBeforeOrEqualTo(after);
    }

    // ── Setters / Getters ─────────────────────────────────────────────────────

    @Test
    @DisplayName("setSender / getSender should round-trip correctly")
    void setSender_shouldRoundTrip() {
        ChatMessage message = new ChatMessage();
        message.setSender("Charlie");
        assertThat(message.getSender()).isEqualTo("Charlie");
    }

    @Test
    @DisplayName("setContent / getContent should round-trip correctly")
    void setContent_shouldRoundTrip() {
        ChatMessage message = new ChatMessage();
        message.setContent("Hello, world!");
        assertThat(message.getContent()).isEqualTo("Hello, world!");
    }

    @Test
    @DisplayName("setType / getType should round-trip correctly for all enum values")
    void setType_shouldRoundTripForAllEnumValues() {
        ChatMessage message = new ChatMessage();

        for (ChatMessage.MessageType type : ChatMessage.MessageType.values()) {
            message.setType(type);
            assertThat(message.getType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("setTimestamp / getTimestamp should round-trip correctly")
    void setTimestamp_shouldRoundTrip() {
        Instant now = Instant.now();
        ChatMessage message = new ChatMessage();
        message.setTimestamp(now);
        assertThat(message.getTimestamp()).isEqualTo(now);
    }

    // ── MessageType enum ──────────────────────────────────────────────────────

    @Test
    @DisplayName("MessageType enum should contain exactly CHAT, JOIN, and LEAVE values")
    void messageTypeEnum_shouldContainExpectedValues() {
        ChatMessage.MessageType[] values = ChatMessage.MessageType.values();
        assertThat(values).containsExactlyInAnyOrder(
                ChatMessage.MessageType.CHAT,
                ChatMessage.MessageType.JOIN,
                ChatMessage.MessageType.LEAVE
        );
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString should include sender, type, and content")
    void toString_shouldIncludeKeyFields() {
        ChatMessage message = new ChatMessage("Dave", "Hello!", ChatMessage.MessageType.CHAT);

        String result = message.toString();

        assertThat(result).contains("Dave");
        assertThat(result).contains("CHAT");
        assertThat(result).contains("Hello!");
    }
}
