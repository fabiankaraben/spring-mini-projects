package com.example.stompwebsockets.unit;

import com.example.stompwebsockets.domain.TopicMessage;
import com.example.stompwebsockets.dto.PublishRequest;
import com.example.stompwebsockets.service.TopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link TopicService}.
 *
 * <p>These tests verify all domain logic in complete isolation – no Spring
 * context, no WebSocket broker, no Docker containers. The service is
 * instantiated manually ({@code new TopicService()}).
 *
 * <ul>
 *   <li><strong>JUnit 5</strong> – {@code @Test}, {@code @BeforeEach}, {@code @DisplayName}</li>
 *   <li><strong>AssertJ</strong> – fluent assertion API ({@code assertThat})</li>
 *   <li><strong>Mockito extension</strong> – present for consistency even though
 *       {@link TopicService} has no injected dependencies to mock.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TopicService – Unit Tests")
class TopicServiceTest {

    /**
     * The class under test – created fresh before every test so state
     * does not leak between test cases.
     */
    private TopicService topicService;

    /** Resets the service state before each test. */
    @BeforeEach
    void setUp() {
        topicService = new TopicService();
    }

    // ── registerTopic ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerTopic should make topicExists return true")
    void registerTopic_shouldMakeTopicExist() {
        topicService.registerTopic("news");

        assertThat(topicService.topicExists("news")).isTrue();
    }

    @Test
    @DisplayName("registerTopic should normalise topic name to lower-case")
    void registerTopic_shouldNormaliseToLowerCase() {
        topicService.registerTopic("SPORTS");

        // Topic should be accessible by its lower-cased name
        assertThat(topicService.topicExists("sports")).isTrue();
        // Upper-case original should also resolve (normalised internally)
        assertThat(topicService.topicExists("SPORTS")).isTrue();
    }

    @Test
    @DisplayName("registerTopic should be idempotent")
    void registerTopic_shouldBeIdempotent() {
        topicService.registerTopic("tech");
        topicService.registerTopic("tech"); // second call must not throw or duplicate

        assertThat(topicService.getTopics()).containsExactly("tech");
    }

    // ── getTopics ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getTopics should return empty list when no topics are registered")
    void getTopics_shouldBeEmptyInitially() {
        assertThat(topicService.getTopics()).isEmpty();
    }

    @Test
    @DisplayName("getTopics should return all registered topics in alphabetical order")
    void getTopics_shouldReturnSortedTopicNames() {
        topicService.registerTopic("sports");
        topicService.registerTopic("news");
        topicService.registerTopic("technology");

        // Service sorts alphabetically for predictable API output
        assertThat(topicService.getTopics())
                .containsExactly("news", "sports", "technology");
    }

    @Test
    @DisplayName("getTopics should return an unmodifiable list")
    void getTopics_shouldReturnUnmodifiableList() {
        topicService.registerTopic("news");

        List<String> topics = topicService.getTopics();

        assertThrows(UnsupportedOperationException.class,
                () -> topics.add("hacked"));
    }

    // ── topicExists ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("topicExists should return false for an unknown topic")
    void topicExists_shouldReturnFalseForUnknownTopic() {
        assertThat(topicService.topicExists("unknown")).isFalse();
    }

    // ── buildAndPublish ───────────────────────────────────────────────────────

    @Test
    @DisplayName("buildAndPublish should return a TopicMessage with the correct topic name")
    void buildAndPublish_shouldSetTopicName() {
        PublishRequest request = new PublishRequest("alice", "Hello!");

        TopicMessage result = topicService.buildAndPublish("news", request);

        assertThat(result.getTopic()).isEqualTo("news");
    }

    @Test
    @DisplayName("buildAndPublish should copy sender and payload from the request")
    void buildAndPublish_shouldCopySenderAndPayload() {
        PublishRequest request = new PublishRequest("alice", "Breaking news!");

        TopicMessage result = topicService.buildAndPublish("news", request);

        assertThat(result.getSender()).isEqualTo("alice");
        assertThat(result.getPayload()).isEqualTo("Breaking news!");
    }

    @Test
    @DisplayName("buildAndPublish should assign a server-side timestamp")
    void buildAndPublish_shouldAssignServerTimestamp() {
        Instant before = Instant.now();
        TopicMessage result = topicService.buildAndPublish("news",
                new PublishRequest("alice", "Hi"));
        Instant after = Instant.now();

        assertThat(result.getTimestamp()).isNotNull();
        assertThat(result.getTimestamp()).isAfterOrEqualTo(before);
        assertThat(result.getTimestamp()).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("buildAndPublish should auto-register the topic if it does not exist")
    void buildAndPublish_shouldAutoRegisterTopic() {
        // Topic "finance" was never explicitly registered
        topicService.buildAndPublish("finance", new PublishRequest("alice", "Market update"));

        assertThat(topicService.topicExists("finance")).isTrue();
    }

    @Test
    @DisplayName("buildAndPublish should add the message to topic history")
    void buildAndPublish_shouldAddMessageToHistory() {
        topicService.buildAndPublish("news", new PublishRequest("alice", "Hello"));

        assertThat(topicService.getHistorySize("news")).isEqualTo(1);
    }

    @Test
    @DisplayName("buildAndPublish should accumulate messages per topic independently")
    void buildAndPublish_shouldAccumulatePerTopicIndependently() {
        topicService.buildAndPublish("news", new PublishRequest("alice", "News 1"));
        topicService.buildAndPublish("news", new PublishRequest("bob", "News 2"));
        topicService.buildAndPublish("sports", new PublishRequest("charlie", "Goal!"));

        assertThat(topicService.getHistorySize("news")).isEqualTo(2);
        assertThat(topicService.getHistorySize("sports")).isEqualTo(1);
    }

    // ── getHistory ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getHistory should return empty list for unknown topic")
    void getHistory_shouldReturnEmptyForUnknownTopic() {
        assertThat(topicService.getHistory("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("getHistory should return messages in insertion order (oldest first)")
    void getHistory_shouldReturnMessagesInInsertionOrder() {
        topicService.buildAndPublish("news", new PublishRequest("alice", "First"));
        topicService.buildAndPublish("news", new PublishRequest("bob", "Second"));
        topicService.buildAndPublish("news", new PublishRequest("charlie", "Third"));

        List<TopicMessage> history = topicService.getHistory("news");

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getPayload()).isEqualTo("First");
        assertThat(history.get(1).getPayload()).isEqualTo("Second");
        assertThat(history.get(2).getPayload()).isEqualTo("Third");
    }

    @Test
    @DisplayName("getHistory should return an unmodifiable list")
    void getHistory_shouldReturnUnmodifiableList() {
        topicService.buildAndPublish("news", new PublishRequest("alice", "Hello"));

        List<TopicMessage> history = topicService.getHistory("news");

        assertThrows(UnsupportedOperationException.class,
                () -> history.add(new TopicMessage()));
    }

    // ── getHistorySize ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getHistorySize should return 0 for an unknown topic")
    void getHistorySize_shouldReturnZeroForUnknownTopic() {
        assertThat(topicService.getHistorySize("unknown")).isEqualTo(0);
    }

    // ── History eviction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("History should not exceed MAX_HISTORY_PER_TOPIC even after many messages")
    void history_shouldEvictOldestWhenAtCapacity() {
        // Fill history to exactly MAX_HISTORY_PER_TOPIC
        for (int i = 0; i < TopicService.MAX_HISTORY_PER_TOPIC; i++) {
            topicService.buildAndPublish("news",
                    new PublishRequest("user" + i, "msg" + i));
        }
        assertThat(topicService.getHistorySize("news"))
                .isEqualTo(TopicService.MAX_HISTORY_PER_TOPIC);

        // One more message should evict the oldest (user0/msg0)
        topicService.buildAndPublish("news",
                new PublishRequest("overflow", "overflow message"));

        assertThat(topicService.getHistorySize("news"))
                .isEqualTo(TopicService.MAX_HISTORY_PER_TOPIC);

        // The oldest message should have been removed
        assertThat(topicService.getHistory("news").get(0).getSender())
                .isEqualTo("user1");
        // The newest message should be at the end
        assertThat(topicService.getHistory("news")
                .get(TopicService.MAX_HISTORY_PER_TOPIC - 1).getSender())
                .isEqualTo("overflow");
    }

    // ── clearHistory ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("clearHistory should remove all messages for a topic")
    void clearHistory_shouldRemoveAllMessages() {
        topicService.buildAndPublish("news", new PublishRequest("alice", "Hello"));
        topicService.buildAndPublish("news", new PublishRequest("bob", "World"));

        topicService.clearHistory("news");

        assertThat(topicService.getHistorySize("news")).isEqualTo(0);
        assertThat(topicService.getHistory("news")).isEmpty();
    }

    @Test
    @DisplayName("clearHistory should not affect other topics")
    void clearHistory_shouldNotAffectOtherTopics() {
        topicService.buildAndPublish("news", new PublishRequest("alice", "News msg"));
        topicService.buildAndPublish("sports", new PublishRequest("bob", "Sports msg"));

        topicService.clearHistory("news");

        // News history cleared; sports history untouched
        assertThat(topicService.getHistorySize("news")).isEqualTo(0);
        assertThat(topicService.getHistorySize("sports")).isEqualTo(1);
    }

    // ── clearAll ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clearAll should remove all topics and all history")
    void clearAll_shouldRemoveEverything() {
        topicService.registerTopic("news");
        topicService.buildAndPublish("news", new PublishRequest("alice", "Hello"));
        topicService.registerTopic("sports");

        topicService.clearAll();

        assertThat(topicService.getTopics()).isEmpty();
        assertThat(topicService.topicExists("news")).isFalse();
        assertThat(topicService.getHistorySize("news")).isEqualTo(0);
    }
}
