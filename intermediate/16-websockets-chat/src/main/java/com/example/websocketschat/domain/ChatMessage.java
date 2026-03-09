package com.example.websocketschat.domain;

import java.time.Instant;

/**
 * Domain model representing a single chat message exchanged between clients.
 *
 * <p>This is a simple value object (no JPA persistence – chat history is
 * in-memory only for this mini-project). Jackson serialises it to JSON
 * automatically when Spring sends it over the STOMP broker.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li><strong>sender</strong> – the username or display name of the person
 *       who sent the message.</li>
 *   <li><strong>content</strong> – the message body text.</li>
 *   <li><strong>type</strong> – distinguishes chat messages from system events
 *       (join / leave notifications).</li>
 *   <li><strong>timestamp</strong> – UTC instant when the server processed the
 *       message, useful for ordering in the UI.</li>
 * </ul>
 */
public class ChatMessage {

    /**
     * Categorises the message so clients can render them differently
     * (e.g., "Alice joined" in italics vs. "Hello!" in a bubble).
     */
    public enum MessageType {
        /** A regular chat message sent by a user. */
        CHAT,
        /** A notification that a user has joined the chat room. */
        JOIN,
        /** A notification that a user has left the chat room. */
        LEAVE
    }

    /** The username/display name of the message author. */
    private String sender;

    /** The body text of the message. */
    private String content;

    /**
     * The kind of message.
     *
     * <p>Defaults to {@link MessageType#CHAT} so that clients that do not
     * set this field explicitly still produce valid messages.
     */
    private MessageType type = MessageType.CHAT;

    /**
     * UTC timestamp assigned by the server when the message is broadcast.
     *
     * <p>Using {@link Instant} ensures the value is always in UTC and is
     * serialised by Jackson as an ISO-8601 string (e.g., {@code 2024-01-01T12:00:00Z}).
     */
    private Instant timestamp;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** No-args constructor required by Jackson for JSON deserialisation. */
    public ChatMessage() {}

    /**
     * Convenience constructor used by the server when creating JOIN / LEAVE
     * system messages.
     *
     * @param sender  the username involved in the event
     * @param content human-readable description of the event
     * @param type    JOIN or LEAVE
     */
    public ChatMessage(String sender, String content, MessageType type) {
        this.sender = sender;
        this.content = content;
        this.type = type;
        this.timestamp = Instant.now();
    }

    // ── Getters and setters ───────────────────────────────────────────────────
    // Spring's Jackson ObjectMapper (used by the STOMP message converter) needs
    // standard JavaBean accessors to serialise/deserialise this class.

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ChatMessage{sender='" + sender + "', type=" + type
                + ", content='" + content + "', timestamp=" + timestamp + "}";
    }
}
