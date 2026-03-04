package com.example.contentnegotiation.controller;

import com.example.contentnegotiation.model.Message;
import com.example.contentnegotiation.service.MessageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller to demonstrate Content Negotiation.
 *
 * By including `jackson-dataformat-xml` in our dependencies, Spring Boot
 * automatically configures HttpMessageConverters to support both JSON and XML.
 *
 * The client dictates the response format by sending an `Accept` header:
 * - Accept: application/json -> Returns JSON
 * - Accept: application/xml -> Returns XML
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Endpoint that returns a Message object.
     * The built-in HttpMessageConverters handle the serialization to either JSON or
     * XML.
     *
     * We can be explicit about what this endpoint produces using the `produces`
     * attribute,
     * though it is optional since Spring Boot handles it implicitly.
     *
     * @return a Message instance
     */
    @GetMapping(produces = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE })
    public Message getMessage() {
        return messageService.getMessage();
    }
}
