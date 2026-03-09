package com.example.stompwebsockets.controller;

import com.example.stompwebsockets.domain.TopicMessage;
import com.example.stompwebsockets.service.TopicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller that bridges HTTP and the STOMP pub/sub world.
 *
 * <h2>Why expose REST endpoints alongside WebSocket?</h2>
 * <p>WebSocket is ideal for real-time streaming, but HTTP/REST is better suited
 * for one-shot administrative operations:
 * <ul>
 *   <li>Creating a topic before clients start publishing.</li>
 *   <li>Listing available topics so a UI can populate a dropdown.</li>
 *   <li>Fetching message history for a topic when a client first connects.</li>
 * </ul>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /api/topics?name={name}} – register a new topic.</li>
 *   <li>{@code GET  /api/topics} – list all registered topics.</li>
 *   <li>{@code GET  /api/topics/{name}/history} – fetch recent messages for a topic.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/topics")
public class TopicRestController {

    /** Topic domain service – manages the topic registry and history. */
    private final TopicService topicService;

    /**
     * Constructor injection.
     *
     * @param topicService the topic domain service
     */
    public TopicRestController(TopicService topicService) {
        this.topicService = topicService;
    }

    /**
     * Registers a new topic.
     *
     * <p>If the topic already exists this is a no-op (idempotent). Topics are
     * lower-cased and trimmed by the service before storage.
     *
     * <pre>
     * POST /api/topics?name=news
     * → 201 Created
     * { "topic": "news", "message": "Topic registered successfully" }
     * </pre>
     *
     * @param name the topic name to create (required query parameter)
     * @return 201 Created with a confirmation body
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createTopic(@RequestParam String name) {
        // Delegate topic creation to the service; normalisation happens there
        topicService.registerTopic(name);
        return ResponseEntity.status(201).body(
                Map.of("topic", name.trim().toLowerCase(),
                       "message", "Topic registered successfully")
        );
    }

    /**
     * Returns all registered topic names.
     *
     * <pre>
     * GET /api/topics
     * → 200 OK
     * ["news", "sports", "technology"]
     * </pre>
     *
     * @return 200 OK with a JSON array of topic name strings
     */
    @GetMapping
    public ResponseEntity<List<String>> listTopics() {
        return ResponseEntity.ok(topicService.getTopics());
    }

    /**
     * Returns recent message history for a specific topic.
     *
     * <p>Useful for clients that connect after messages have already been
     * published: they can fetch the last {@link TopicService#MAX_HISTORY_PER_TOPIC}
     * messages via HTTP before subscribing over STOMP.
     *
     * <pre>
     * GET /api/topics/news/history
     * → 200 OK   (with list of TopicMessage objects)
     * → 404 Not Found  (if topic does not exist)
     * </pre>
     *
     * @param name the topic name
     * @return 200 OK with message history, or 404 if the topic does not exist
     */
    @GetMapping("/{name}/history")
    public ResponseEntity<List<TopicMessage>> getHistory(@PathVariable String name) {
        // Return 404 if the topic hasn't been registered or published to
        if (!topicService.topicExists(name)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(topicService.getHistory(name));
    }
}
