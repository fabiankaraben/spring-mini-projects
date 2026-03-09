package com.example.stompwebsockets.domain;

import java.time.Instant;

/**
 * Domain model representing a message published to a STOMP topic.
 *
 * <p>A {@code TopicMessage} is the unit of data that travels from the publisher
 * (a client that sends to {@code /app/topic/{name}}) through the controller and
 * in-memory broker to all subscribers of {@code /topic/{name}}.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li><strong>topic</strong> – the name of the topic this message belongs to
 *       (e.g., {@code "news"}, {@code "sports"}).</li>
 *   <li><strong>sender</strong> – the username or display name of the publisher.</li>
 *   <li><strong>payload</strong> – the message body (arbitrary text content).</li>
 *   <li><strong>timestamp</strong> – UTC instant assigned by the server when the
 *       message is processed; clients cannot spoof this value.</li>
 * </ul>
 *
 * <p>Jackson serialises this class to JSON automatically because Spring's STOMP
 * message converter is configured with a {@code MappingJackson2MessageConverter}
 * under the hood.
 */
public class TopicMessage {

    /** The topic (channel) name this message was published to. */
    private String topic;

    /** The username or display name of the message publisher. */
    private String sender;

    /** The body of the published message. */
    private String payload;

    /**
     * UTC timestamp set by the server when the message is processed.
     *
     * <p>Using {@link Instant} guarantees UTC and Jackson serialises it as an
     * ISO-8601 string (e.g., {@code 2024-06-01T10:30:00Z}).
     */
    private Instant timestamp;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** No-args constructor required by Jackson for JSON deserialisation. */
    public TopicMessage() {}

    /**
     * Full constructor used by the service layer to build broadcast messages.
     *
     * @param topic     the topic (channel) name
     * @param sender    the publisher's username
     * @param payload   the message body
     * @param timestamp the server-assigned UTC timestamp
     */
    public TopicMessage(String topic, String sender, String payload, Instant timestamp) {
        this.topic = topic;
        this.sender = sender;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    // ── Getters and setters ───────────────────────────────────────────────────
    // Standard JavaBean accessors required by Jackson and Spring's message converter.

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "TopicMessage{topic='" + topic + "', sender='" + sender
                + "', payload='" + payload + "', timestamp=" + timestamp + "}";
    }
}
