package com.example.contentnegotiation.service;

import com.example.contentnegotiation.model.Message;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service class for handling Message business logic.
 *
 * Extracting logic into a service allows for easier unit testing
 * with Mockito without needing to load the entire web context.
 */
@Service
public class MessageService {

    /**
     * Generates a sample Message.
     *
     * @return a Message instance
     */
    public Message getMessage() {
        return new Message(
                UUID.randomUUID().toString(),
                "Hello, this is a response demonstrating Content Negotiation!");
    }
}
