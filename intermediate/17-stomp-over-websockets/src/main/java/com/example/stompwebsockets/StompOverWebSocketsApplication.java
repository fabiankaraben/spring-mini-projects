package com.example.stompwebsockets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the STOMP over WebSockets mini-project.
 *
 * <h2>What this project demonstrates</h2>
 * <ul>
 *   <li><strong>Pub/Sub routing</strong> – clients subscribe to named topics
 *       (channels) and receive only the messages published to those topics.</li>
 *   <li><strong>Private messaging</strong> – users can send messages directly
 *       to another user via the {@code /user} destination prefix, which Spring
 *       routes exclusively to that user's session.</li>
 *   <li><strong>Channel management</strong> – a REST API allows creating topics
 *       and listing active subscribers, bridging HTTP and WebSocket worlds.</li>
 *   <li><strong>STOMP protocol</strong> – the Simple Text Oriented Messaging
 *       Protocol adds pub/sub semantics (SUBSCRIBE, SEND, UNSUBSCRIBE) on top
 *       of raw WebSocket frames, making it easy to build reactive UIs.</li>
 * </ul>
 *
 * <h2>Architecture overview</h2>
 * <pre>
 *   HTTP client  ─── REST /api/topics ──────────────► TopicController (REST)
 *                                                              │
 *   WS client ─── STOMP SEND /app/topic/{name} ──────► TopicMessageController
 *              └── STOMP SEND /app/private ──────────► PrivateMessageController
 *                                                              │
 *                                                    In-memory STOMP broker
 *                                                              │
 *              ┌── STOMP SUBSCRIBE /topic/{name} ◄────────────┘
 *              └── STOMP SUBSCRIBE /user/queue/private ◄──────┘
 * </pre>
 */
@SpringBootApplication
public class StompOverWebSocketsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StompOverWebSocketsApplication.class, args);
    }
}
