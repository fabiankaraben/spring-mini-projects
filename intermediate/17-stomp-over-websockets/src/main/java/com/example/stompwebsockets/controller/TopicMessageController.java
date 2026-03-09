package com.example.stompwebsockets.controller;

import com.example.stompwebsockets.domain.TopicMessage;
import com.example.stompwebsockets.dto.PublishRequest;
import com.example.stompwebsockets.service.TopicService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

/**
 * STOMP WebSocket controller that handles pub/sub message publishing.
 *
 * <h2>Pub/Sub routing explained</h2>
 * <p>When a client sends a STOMP SEND frame to {@code /app/topic/news}, Spring:
 * <ol>
 *   <li>Strips the {@code /app} prefix (application destination prefix).</li>
 *   <li>Matches the remaining path {@code /topic/news} to the
 *       {@code @MessageMapping("/topic/{name}")} method below.</li>
 *   <li>Injects the path variable {@code name = "news"} and deserialises
 *       the JSON body into a {@link PublishRequest}.</li>
 *   <li>Invokes {@link #publish}, which returns a {@link TopicMessage}.</li>
 *   <li>The {@code @SendTo} annotation causes Spring to broadcast the return
 *       value to the destination {@code /topic/news}, where all subscribers
 *       receive it via the in-memory STOMP broker.</li>
 * </ol>
 *
 * <h2>Why {@code @Controller} instead of {@code @RestController}?</h2>
 * <p>This controller handles STOMP messages, not HTTP requests. The return
 * value is forwarded to the broker and serialised by the STOMP message
 * converter (Jackson), not written to an HTTP response body.
 */
@Controller
public class TopicMessageController {

    /** Service that builds messages and maintains per-topic history. */
    private final TopicService topicService;

    /**
     * Constructor injection – makes the dependency explicit and testable.
     *
     * @param topicService the topic domain service
     */
    public TopicMessageController(TopicService topicService) {
        this.topicService = topicService;
    }

    /**
     * Handles a STOMP SEND to {@code /app/topic/{name}}.
     *
     * <p>Workflow:
     * <ol>
     *   <li>Receives the validated {@link PublishRequest} payload.</li>
     *   <li>Delegates to {@link TopicService#buildAndPublish} to build the
     *       {@link TopicMessage} with a server-assigned timestamp.</li>
     *   <li>Returns the message; {@code @SendTo} broadcasts it to all
     *       subscribers of {@code /topic/{name}}.</li>
     * </ol>
     *
     * @param name    the topic name extracted from the destination path
     * @param request the incoming publish payload (validated by Spring)
     * @return the {@link TopicMessage} broadcast to all topic subscribers
     */
    @MessageMapping("/topic/{name}")          // Handles STOMP SEND to /app/topic/{name}
    @SendTo("/topic/{name}")                   // Broadcasts the return value to all /topic/{name} subscribers
    public TopicMessage publish(
            @DestinationVariable String name,  // Extracts {name} from the destination path
            @Validated PublishRequest request  // Validated JSON payload from the client
    ) {
        // Delegate to the service – it assigns server-side metadata and stores history
        return topicService.buildAndPublish(name, request);
    }
}
