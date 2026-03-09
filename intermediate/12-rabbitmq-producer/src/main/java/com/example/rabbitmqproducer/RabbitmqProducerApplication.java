package com.example.rabbitmqproducer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the RabbitMQ Producer Spring Boot application.
 *
 * <p>This mini-project demonstrates how a Spring Boot backend can publish
 * asynchronous messages to a RabbitMQ exchange using Spring AMQP's
 * {@link org.springframework.amqp.rabbit.core.RabbitTemplate}.
 *
 * <p>Key concepts illustrated:
 * <ul>
 *   <li><strong>Exchange</strong> – a routing entity in RabbitMQ that receives
 *       messages from producers and routes them to one or more queues.</li>
 *   <li><strong>Queue</strong> – a buffer that stores messages until a consumer
 *       retrieves and processes them.</li>
 *   <li><strong>Routing key</strong> – a string attached to each message that the
 *       exchange uses to decide which queue(s) to deliver it to.</li>
 *   <li><strong>Direct exchange</strong> – routes messages to queues whose binding
 *       key exactly matches the routing key of the published message.</li>
 * </ul>
 *
 * <p>The application exposes a REST API at {@code /api/messages} that clients can
 * call to send a JSON payload to RabbitMQ. The message is published to a
 * <em>direct exchange</em> and routed to a durable queue named
 * {@code orders.queue}.
 */
@SpringBootApplication
public class RabbitmqProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitmqProducerApplication.class, args);
    }
}
