package com.example.stompwebsockets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for a message-publish request.
 *
 * <p>Clients send this JSON payload when they want to publish a message to a topic:
 * <pre>
 * {
 *   "sender":  "alice",
 *   "payload": "Hello, subscribers!"
 * }
 * </pre>
 *
 * <h2>Why a separate DTO?</h2>
 * <p>Accepting a DTO (instead of the domain {@code TopicMessage} directly) has
 * several benefits:
 * <ul>
 *   <li>Client-supplied fields are validated here before reaching the service.</li>
 *   <li>The server controls security-sensitive fields (timestamp, topic name)
 *       that should not be trusted from the client.</li>
 *   <li>The API surface is minimal and explicit.</li>
 * </ul>
 */
public class PublishRequest {

    /**
     * The display name / username of the publisher.
     *
     * <p>Must not be blank. Maximum 64 characters to prevent abuse.
     */
    @NotBlank(message = "sender must not be blank")
    @Size(max = 64, message = "sender must not exceed 64 characters")
    private String sender;

    /**
     * The message body to publish.
     *
     * <p>Must not be blank. Maximum 2 000 characters per message.
     */
    @NotBlank(message = "payload must not be blank")
    @Size(max = 2000, message = "payload must not exceed 2000 characters")
    private String payload;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** No-args constructor required by Jackson for JSON deserialisation. */
    public PublishRequest() {}

    /**
     * Convenience constructor used in tests to build request objects without
     * going through JSON serialisation.
     *
     * @param sender  the publisher's username
     * @param payload the message body
     */
    public PublishRequest(String sender, String payload) {
        this.sender = sender;
        this.payload = payload;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
