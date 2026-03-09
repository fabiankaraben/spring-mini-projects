package com.example.rabbitmqconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the RabbitMQ Consumer Spring Boot application.
 *
 * <p>This mini-project demonstrates how a Spring Boot backend can consume
 * asynchronous messages from a RabbitMQ queue using Spring AMQP's
 * {@link org.springframework.amqp.rabbit.annotation.RabbitListener} annotation.
 *
 * <p>Key concepts illustrated:
 * <ul>
 *   <li><strong>@RabbitListener</strong> – marks a method as a message listener
 *       on a specific queue. Spring AMQP starts a dedicated listener container
 *       that polls the queue and invokes the annotated method for each message.</li>
 *   <li><strong>Message acknowledgement</strong> – by default Spring AMQP uses
 *       AUTO ack mode: the message is acknowledged after the listener method
 *       returns normally, or rejected if it throws an exception.</li>
 *   <li><strong>Dead Letter Queue (DLQ)</strong> – failed messages are routed
 *       to a dead-letter queue so they are not lost and can be inspected or
 *       retried later.</li>
 *   <li><strong>JSON deserialization</strong> – the
 *       {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter}
 *       converts the raw JSON AMQP message body back into a Java object before
 *       the listener method is called.</li>
 * </ul>
 *
 * <p>The application also exposes a REST API at {@code /api/messages} that
 * returns statistics about consumed messages (count, last received, etc.).
 * This makes it easy to verify from a browser or {@code curl} that messages
 * are being processed.
 */
@SpringBootApplication
public class RabbitmqConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitmqConsumerApplication.class, args);
    }
}
