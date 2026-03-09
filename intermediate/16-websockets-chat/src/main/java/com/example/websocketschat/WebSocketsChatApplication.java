package com.example.websocketschat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the WebSockets Chat mini-project.
 *
 * <p>This application demonstrates how to use Spring WebSocket with STOMP
 * (Simple Text Oriented Messaging Protocol) and SockJS fallback to build a
 * real-time chat system.
 *
 * <h2>Architecture overview</h2>
 * <ul>
 *   <li>Clients connect via WebSocket (or SockJS) to {@code /ws}.</li>
 *   <li>They subscribe to topic {@code /topic/messages} to receive broadcasts.</li>
 *   <li>They send messages to destination {@code /app/chat.send}.</li>
 *   <li>The broker relays messages from {@code /app/**} to all subscribers on
 *       the matching {@code /topic/**} destination.</li>
 * </ul>
 */
@SpringBootApplication
public class WebSocketsChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebSocketsChatApplication.class, args);
    }
}
