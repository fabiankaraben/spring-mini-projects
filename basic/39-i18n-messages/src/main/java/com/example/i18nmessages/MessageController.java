package com.example.i18nmessages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * REST Controller for handling localized messages.
 * This controller provides an endpoint to retrieve messages based on the provided key,
 * localized according to the Accept-Language header in the HTTP request.
 */
@RestController
public class MessageController {

    // MessageSource is automatically configured by Spring Boot to load messages from messages.properties files
    private final MessageSource messageSource;

    /**
     * Constructor injection of MessageSource.
     * @param messageSource the MessageSource bean provided by Spring
     */
    @Autowired
    public MessageController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Endpoint to get a localized message by key.
     * The localization is determined by the Accept-Language header in the request.
     * @param key the message key to look up
     * @param locale the locale resolved from the request (injected by Spring)
     * @return the localized message string
     */
    @GetMapping("/message/{key}")
    public String getMessage(@PathVariable String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}
