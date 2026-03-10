package com.example.activemqjms.service;

import com.example.activemqjms.domain.OrderMessage;
import com.example.activemqjms.dto.OrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

/**
 * Service layer responsible for constructing and publishing order messages
 * to an ActiveMQ queue via JMS.
 *
 * <p>This class sits between the HTTP controller and the JMS broker:
 * <ol>
 *   <li>The controller receives the HTTP request and validates the body (DTO).</li>
 *   <li>This service maps the DTO to the domain message object, enriching it
 *       with auto-generated fields ({@code messageId}, {@code createdAt}).</li>
 *   <li>{@link JmsTemplate#convertAndSend} serialises the domain object to JSON
 *       and publishes it to the configured destination queue.</li>
 * </ol>
 *
 * <h2>JMS point-to-point model (Queue)</h2>
 * <p>This producer uses a <em>queue</em> (point-to-point domain), which means:
 * <ul>
 *   <li>Each message is delivered to exactly one consumer.</li>
 *   <li>Messages are durable: they persist in the broker until a consumer
 *       acknowledges them, even across broker restarts.</li>
 *   <li>Contrast with a <em>topic</em> (publish-subscribe), where every
 *       subscribed consumer receives every message.</li>
 * </ul>
 *
 * <h2>Asynchronous semantics</h2>
 * <p>{@link JmsTemplate#convertAndSend} is a <em>fire-and-forget</em> call:
 * it publishes the message to the broker and returns immediately without waiting
 * for a consumer to process it. This decouples the HTTP response time from the
 * downstream processing latency.
 *
 * <h2>Why separate the service from the controller?</h2>
 * <ul>
 *   <li><strong>Testability</strong>: the service can be unit-tested by mocking
 *       {@link JmsTemplate}, without starting a real broker or HTTP server.</li>
 *   <li><strong>Single Responsibility</strong>: the controller handles HTTP
 *       concerns; this service handles messaging concerns.</li>
 *   <li><strong>Reusability</strong>: other components (scheduled jobs, event
 *       listeners) can publish messages by calling this service directly.</li>
 * </ul>
 */
@Service
public class MessageProducerService {

    private static final Logger log = LoggerFactory.getLogger(MessageProducerService.class);

    /**
     * Spring's primary JMS abstraction for sending messages.
     *
     * <p>The template is pre-configured with a JSON message converter in
     * {@link com.example.activemqjms.config.JmsConfig}, so calling
     * {@code convertAndSend} with a Java object automatically serialises it
     * to a JSON text message with a {@code _type} JMS property header.
     */
    private final JmsTemplate jmsTemplate;

    /**
     * Name of the JMS destination (queue) to which messages are published.
     * Injected from {@code app.jms.orders-queue} in application.yml.
     */
    @Value("${app.jms.orders-queue}")
    private String ordersQueue;

    /**
     * Constructor injection makes the {@link JmsTemplate} dependency explicit
     * and allows unit tests to supply a mock without loading the Spring context.
     *
     * @param jmsTemplate the configured JMS template for sending messages
     */
    public MessageProducerService(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    /**
     * Publish an order message to the ActiveMQ queue.
     *
     * <p>Steps performed by this method:
     * <ol>
     *   <li>Map the {@link OrderRequest} DTO to an {@link OrderMessage} domain
     *       object. The {@link OrderMessage} constructor auto-generates the
     *       {@code messageId} (UUID) and {@code createdAt} (current UTC time).</li>
     *   <li>Call {@link JmsTemplate#convertAndSend} which:
     *       <ul>
     *         <li>Serialises the {@link OrderMessage} to a JSON {@link jakarta.jms.TextMessage}
     *             using the {@link org.springframework.jms.support.converter.MappingJackson2MessageConverter}.</li>
     *         <li>Publishes the JMS message to {@code ordersQueue}.</li>
     *         <li>The broker holds the message until a consumer acknowledges it.</li>
     *       </ul>
     *   </li>
     *   <li>Log the published message for observability.</li>
     *   <li>Return the domain object so the controller can include it in the
     *       HTTP response, confirming to the caller exactly what was sent.</li>
     * </ol>
     *
     * @param request the validated order request from the HTTP body
     * @return the {@link OrderMessage} that was published to the broker
     */
    public OrderMessage publishOrder(OrderRequest request) {
        // Map DTO → domain message; messageId and createdAt are auto-generated
        OrderMessage message = new OrderMessage(
                request.getOrderId(),
                request.getProduct(),
                request.getQuantity()
        );

        log.info("Publishing order message to queue='{}': {}", ordersQueue, message);

        // convertAndSend serialises the Java object to JSON and publishes it to the queue.
        // This is a fire-and-forget operation: returns as soon as the broker receives the message.
        jmsTemplate.convertAndSend(ordersQueue, message);

        log.info("Successfully published message with id={}", message.getMessageId());

        // Return the message so the controller can confirm the published payload to the caller
        return message;
    }
}
