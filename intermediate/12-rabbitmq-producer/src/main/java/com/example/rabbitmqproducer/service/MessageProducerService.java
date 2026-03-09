package com.example.rabbitmqproducer.service;

import com.example.rabbitmqproducer.domain.OrderMessage;
import com.example.rabbitmqproducer.dto.OrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service layer responsible for constructing and publishing order messages
 * to a RabbitMQ exchange.
 *
 * <p>This class sits between the HTTP controller and the AMQP broker:
 * <ol>
 *   <li>The controller receives the HTTP request and validates the body (DTO).</li>
 *   <li>This service maps the DTO to the domain message object, enriching it
 *       with auto-generated fields ({@code messageId}, {@code createdAt}).</li>
 *   <li>{@link RabbitTemplate#convertAndSend} serialises the domain object to
 *       JSON and publishes it to the configured exchange with the routing key.</li>
 * </ol>
 *
 * <h2>Why separate the service from the controller?</h2>
 * <ul>
 *   <li><strong>Testability</strong>: the service can be unit-tested by mocking
 *       {@link RabbitTemplate}, without starting a real broker or HTTP server.</li>
 *   <li><strong>Single Responsibility</strong>: the controller handles HTTP
 *       concerns; this service handles messaging concerns.</li>
 *   <li><strong>Reusability</strong>: other components (scheduled jobs, event
 *       listeners) can publish messages by calling this service directly.</li>
 * </ul>
 *
 * <h2>Asynchronous semantics</h2>
 * <p>{@link RabbitTemplate#convertAndSend} is a <em>fire-and-forget</em> call:
 * it publishes the message to the broker and returns immediately without waiting
 * for a consumer to process it. This makes the HTTP response time independent of
 * how long the downstream consumer takes to handle the order.
 */
@Service
public class MessageProducerService {

    private static final Logger log = LoggerFactory.getLogger(MessageProducerService.class);

    /**
     * Spring AMQP's primary template for publishing messages to a RabbitMQ broker.
     *
     * <p>The template is pre-configured with a JSON message converter in
     * {@link com.example.rabbitmqproducer.config.RabbitMQConfig}, so calling
     * {@code convertAndSend} with a Java object automatically serialises it
     * to a JSON byte array with the correct {@code content_type} AMQP header.
     */
    private final RabbitTemplate rabbitTemplate;

    /**
     * Name of the direct exchange to publish messages to.
     * Injected from {@code spring.rabbitmq.exchange} in application.yml.
     */
    @Value("${spring.rabbitmq.exchange}")
    private String exchangeName;

    /**
     * Routing key used when publishing messages to the exchange.
     * The exchange uses this key to route the message to the bound queue.
     * Injected from {@code spring.rabbitmq.routing-key} in application.yml.
     */
    @Value("${spring.rabbitmq.routing-key}")
    private String routingKey;

    /**
     * Constructor injection makes the {@link RabbitTemplate} dependency explicit
     * and allows unit tests to supply a mock without loading the Spring context.
     *
     * @param rabbitTemplate the configured AMQP template for sending messages
     */
    public MessageProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publish an order message to the RabbitMQ exchange.
     *
     * <p>Steps performed by this method:
     * <ol>
     *   <li>Map the {@link OrderRequest} DTO to an {@link OrderMessage} domain
     *       object. The {@link OrderMessage} constructor auto-generates the
     *       {@code messageId} (UUID) and {@code createdAt} (current UTC time).</li>
     *   <li>Call {@link RabbitTemplate#convertAndSend} which:
     *       <ul>
     *         <li>Serialises the {@link OrderMessage} to JSON using the
     *             {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter}.</li>
     *         <li>Publishes the AMQP message to {@code exchangeName} with
     *             {@code routingKey}.</li>
     *         <li>The exchange routes the message to the bound queue.</li>
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

        log.info("Publishing order message to exchange='{}' with routingKey='{}': {}",
                exchangeName, routingKey, message);

        // convertAndSend serialises the object to JSON and publishes it to the exchange.
        // This is a fire-and-forget operation: it returns as soon as the message is
        // handed off to the broker, without waiting for any consumer to process it.
        rabbitTemplate.convertAndSend(exchangeName, routingKey, message);

        log.info("Successfully published message with id={}", message.getMessageId());

        // Return the message so the controller can confirm the published payload to the caller
        return message;
    }
}
