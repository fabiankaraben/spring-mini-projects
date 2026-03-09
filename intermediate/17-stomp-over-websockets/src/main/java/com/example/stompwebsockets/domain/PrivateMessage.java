package com.example.stompwebsockets.domain;

import java.time.Instant;

/**
 * Domain model representing a private (direct) message between two users.
 *
 * <p>Unlike a {@link TopicMessage} which is broadcast to all topic subscribers,
 * a {@code PrivateMessage} is routed by Spring's user-destination mechanism
 * exclusively to the intended recipient's STOMP session(s).
 *
 * <h2>User destinations in Spring STOMP</h2>
 * <p>When the server calls
 * {@code SimpMessageSendingOperations.convertAndSendToUser(recipient, "/queue/private", message)},
 * Spring rewrites the destination to {@code /user/{recipient}/queue/private} and
 * the in-memory broker delivers it only to sessions whose principal name matches
 * {@code recipient}. This means:
 * <ul>
 *   <li>The recipient must subscribe to {@code /user/queue/private} (Spring automatically
 *       expands this to include the user-specific prefix).</li>
 *   <li>No other subscriber receives the message.</li>
 * </ul>
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li><strong>sender</strong> – the username of the message author.</li>
 *   <li><strong>recipient</strong> – the username of the intended receiver.</li>
 *   <li><strong>content</strong> – the message text.</li>
 *   <li><strong>timestamp</strong> – UTC instant set by the server.</li>
 * </ul>
 */
public class PrivateMessage {

    /** Username of the user who wrote the message. */
    private String sender;

    /** Username of the user this message is addressed to. */
    private String recipient;

    /** The body text of the private message. */
    private String content;

    /**
     * UTC timestamp assigned by the server when the message is dispatched.
     *
     * <p>Server-assigned timestamps prevent clock-skew issues between clients
     * and ensure a consistent ordering of messages on the recipient's side.
     */
    private Instant timestamp;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** No-args constructor required by Jackson for JSON deserialisation. */
    public PrivateMessage() {}

    /**
     * Full constructor used by the service layer when building the dispatched message.
     *
     * @param sender    username of the sender
     * @param recipient username of the intended recipient
     * @param content   message body text
     * @param timestamp server-assigned UTC timestamp
     */
    public PrivateMessage(String sender, String recipient, String content, Instant timestamp) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.timestamp = timestamp;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "PrivateMessage{sender='" + sender + "', recipient='" + recipient
                + "', content='" + content + "', timestamp=" + timestamp + "}";
    }
}
