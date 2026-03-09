package com.example.stompwebsockets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for sending a private (direct) message.
 *
 * <p>Clients send this JSON payload when they want to deliver a message
 * to a specific user rather than broadcasting it to a topic:
 * <pre>
 * {
 *   "sender":    "alice",
 *   "recipient": "bob",
 *   "content":   "Hey Bob, are you there?"
 * }
 * </pre>
 *
 * <p>The server routes this message exclusively to {@code bob}'s STOMP session
 * using Spring's user-destination mechanism ({@code convertAndSendToUser}).
 * No other client receives the message.
 */
public class PrivateMessageRequest {

    /**
     * The username of the user sending the message.
     *
     * <p>Must not be blank; max 64 characters.
     */
    @NotBlank(message = "sender must not be blank")
    @Size(max = 64, message = "sender must not exceed 64 characters")
    private String sender;

    /**
     * The username of the intended recipient.
     *
     * <p>Spring uses this value to look up active STOMP sessions whose
     * principal name matches this string.
     */
    @NotBlank(message = "recipient must not be blank")
    @Size(max = 64, message = "recipient must not exceed 64 characters")
    private String recipient;

    /**
     * The message body to deliver privately.
     *
     * <p>Must not be blank; max 2 000 characters.
     */
    @NotBlank(message = "content must not be blank")
    @Size(max = 2000, message = "content must not exceed 2000 characters")
    private String content;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** No-args constructor required by Jackson for JSON deserialisation. */
    public PrivateMessageRequest() {}

    /**
     * Convenience constructor used in tests.
     *
     * @param sender    the sender's username
     * @param recipient the recipient's username
     * @param content   the message body
     */
    public PrivateMessageRequest(String sender, String recipient, String content) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
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
}
