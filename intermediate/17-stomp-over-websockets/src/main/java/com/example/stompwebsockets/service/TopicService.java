package com.example.stompwebsockets.service;

import com.example.stompwebsockets.domain.TopicMessage;
import com.example.stompwebsockets.dto.PublishRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Domain service that manages pub/sub topics and message history.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Maintain a registry of known topics (channels).</li>
 *   <li>Build {@link TopicMessage} objects from {@link PublishRequest} DTOs,
 *       assigning server-controlled metadata (timestamp, topic name).</li>
 *   <li>Keep a bounded in-memory history of recent messages per topic so that
 *       newly subscribing clients can retrieve recent context.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>The topic registry uses {@link ConcurrentHashMap} and each per-topic
 * history list uses {@link CopyOnWriteArrayList} so that concurrent WebSocket
 * connections can safely read and write without explicit locks.
 *
 * <h2>Why keep this logic in a service?</h2>
 * <p>Isolating business logic here makes it trivially unit-testable: tests can
 * call {@code buildAndPublish()} directly without any Spring context, broker,
 * or WebSocket infrastructure.
 */
@Service
public class TopicService {

    /**
     * Maximum number of messages kept in history per topic.
     *
     * <p>When this limit is reached the oldest message is evicted so that
     * server memory does not grow without bound.
     */
    public static final int MAX_HISTORY_PER_TOPIC = 50;

    /**
     * Registry of known topics.
     *
     * <p>A topic is created either explicitly via the REST API or implicitly
     * the first time a message is published to it. The {@link Set} value is
     * unused; {@link ConcurrentHashMap} is used as a concurrent set.
     */
    private final Map<String, Boolean> topicRegistry = new ConcurrentHashMap<>();

    /**
     * Per-topic message history.
     *
     * <p>Key: topic name. Value: list of recent {@link TopicMessage} objects,
     * oldest first, bounded by {@link #MAX_HISTORY_PER_TOPIC}.
     */
    private final Map<String, List<TopicMessage>> topicHistory = new ConcurrentHashMap<>();

    // ── Topic registry ────────────────────────────────────────────────────────

    /**
     * Registers a topic by name.
     *
     * <p>If the topic already exists this is a no-op; topics are idempotently
     * created. The topic name is trimmed and lower-cased for consistency.
     *
     * @param name the topic name to register (must not be blank)
     */
    public void registerTopic(String name) {
        // Normalise the name to lower-case so "News" and "news" are the same topic
        String normalised = name.trim().toLowerCase();
        topicRegistry.put(normalised, Boolean.TRUE);
        // Also ensure a history list exists for this topic
        topicHistory.computeIfAbsent(normalised, k -> new CopyOnWriteArrayList<>());
    }

    /**
     * Returns an unmodifiable snapshot of all registered topic names.
     *
     * @return sorted set of topic names as an unmodifiable list
     */
    public List<String> getTopics() {
        List<String> topics = new ArrayList<>(topicRegistry.keySet());
        Collections.sort(topics); // stable alphabetical order for predictable responses
        return Collections.unmodifiableList(topics);
    }

    /**
     * Returns whether a topic with the given name exists.
     *
     * @param name the topic name to check
     * @return {@code true} if the topic is registered
     */
    public boolean topicExists(String name) {
        return topicRegistry.containsKey(name.trim().toLowerCase());
    }

    // ── Message building and history ──────────────────────────────────────────

    /**
     * Builds a {@link TopicMessage} from the incoming request and stores it in
     * the per-topic history.
     *
     * <p>The topic is auto-registered if it does not yet exist (implicit creation
     * on first publish). The server assigns the timestamp so clients cannot spoof
     * the time or topic fields.
     *
     * @param topicName the target topic name
     * @param request   the validated publish request from the client
     * @return the fully-built {@link TopicMessage} ready to be broadcast
     */
    public TopicMessage buildAndPublish(String topicName, PublishRequest request) {
        String normalised = topicName.trim().toLowerCase();

        // Auto-register the topic if it has not been explicitly created yet
        registerTopic(normalised);

        // Build the message with server-assigned metadata
        TopicMessage message = new TopicMessage(
                normalised,
                request.getSender(),
                request.getPayload(),
                Instant.now() // server controls the timestamp
        );

        // Store in per-topic history, evicting oldest if at capacity
        storeInHistory(normalised, message);
        return message;
    }

    /**
     * Returns the message history for a specific topic.
     *
     * @param topicName the topic name
     * @return unmodifiable list of recent messages, oldest first;
     *         empty list if the topic is unknown or has no history
     */
    public List<TopicMessage> getHistory(String topicName) {
        String normalised = topicName.trim().toLowerCase();
        List<TopicMessage> history = topicHistory.get(normalised);
        if (history == null) {
            return Collections.emptyList();
        }
        // Return an unmodifiable snapshot to prevent external mutation
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /**
     * Returns the number of messages currently in the history for a topic.
     *
     * @param topicName the topic name
     * @return current history size; 0 if the topic has no history
     */
    public int getHistorySize(String topicName) {
        String normalised = topicName.trim().toLowerCase();
        List<TopicMessage> history = topicHistory.get(normalised);
        return history == null ? 0 : history.size();
    }

    /**
     * Clears all message history for a topic without removing the topic itself.
     *
     * <p>Useful for tests and for future administrative endpoints.
     *
     * @param topicName the topic whose history should be cleared
     */
    public void clearHistory(String topicName) {
        String normalised = topicName.trim().toLowerCase();
        List<TopicMessage> history = topicHistory.get(normalised);
        if (history != null) {
            history.clear();
        }
    }

    /**
     * Removes all registered topics and all history.
     *
     * <p>Used in tests to reset state between test cases.
     */
    public void clearAll() {
        topicRegistry.clear();
        topicHistory.clear();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Appends a message to the topic's history list, evicting the oldest entry
     * when the {@link #MAX_HISTORY_PER_TOPIC} limit is reached.
     *
     * @param topicName the normalised topic name
     * @param message   the message to store
     */
    private void storeInHistory(String topicName, TopicMessage message) {
        List<TopicMessage> history = topicHistory.computeIfAbsent(
                topicName, k -> new CopyOnWriteArrayList<>());

        if (history.size() >= MAX_HISTORY_PER_TOPIC) {
            // Evict the oldest message (index 0 = insertion order head)
            history.remove(0);
        }
        history.add(message);
    }
}
