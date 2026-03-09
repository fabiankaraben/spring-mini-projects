package com.example.websocketschat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for incoming chat message payloads.
 *
 * <p>When a STOMP client sends a message to {@code /app/chat.send}, the
 * JSON payload is deserialised into this object by Spring's Jackson message
 * converter. Bean Validation annotations on the fields are enforced by
 * {@code @Validated} in the controller.
 *
 * <h2>Why a separate DTO instead of using ChatMessage directly?</h2>
 * <ul>
 *   <li>Separates the incoming wire format from the internal domain model.</li>
 *   <li>The client should not be able to set the {@code timestamp} or
 *       {@code type} fields – those are controlled by the server.</li>
 *   <li>Makes it easy to add request-only fields (e.g., a client-side
 *       message ID for deduplication) without polluting the domain class.</li>
 * </ul>
 */
public class SendMessageRequest {

    /**
     * The display name of the sender.
     *
     * <p>Must not be blank and is capped at 50 characters to prevent
     * excessively long usernames from being broadcast.
     */
    @NotBlank(message = "Sender must not be blank")
    @Size(max = 50, message = "Sender name must be at most 50 characters")
    private String sender;

    /**
     * The message body.
     *
     * <p>Optional for JOIN and LEAVE events – the server generates the
     * human-readable text for those. For CHAT messages a non-blank value
     * is expected by convention but not enforced at the DTO level so that
     * JOIN/LEAVE frames (which set content to an empty string) still pass
     * Bean Validation. Capped at 1000 characters to prevent oversized payloads.
     */
    @Size(max = 1000, message = "Message content must be at most 1000 characters")
    private String content;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** No-args constructor required by Jackson. */
    public SendMessageRequest() {}

    /**
     * Constructor for test convenience.
     *
     * @param sender  the sender's display name
     * @param content the message body
     */
    public SendMessageRequest(String sender, String content) {
        this.sender = sender;
        this.content = content;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

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
}
