package com.example.rabbitmqconsumer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology and infrastructure configuration for the consumer.
 *
 * <p>This configuration declares the same exchange, queue, and binding topology
 * as the producer. Having both sides declare the topology is a common pattern
 * in microservices: each service is self-contained and idempotently ensures that
 * the required RabbitMQ resources exist before it starts consuming.
 *
 * <h2>RabbitMQ topology consumed by this application</h2>
 * <pre>
 *   [orders.exchange]  ← Direct Exchange (declared by producer, re-declared here)
 *       │  routing key = "orders.routing.key"
 *       ▼
 *   [orders.queue]     ← Durable Queue
 *       │
 *       ▼
 *   [OrderMessageListener] ← @RabbitListener reads messages from here
 *       │
 *       ▼  (on processing failure)
 *   [orders.queue.dlq] ← Dead Letter Queue (DLQ)
 * </pre>
 *
 * <h2>Dead Letter Queue (DLQ)</h2>
 * <p>The main queue is configured with a dead-letter exchange. When a message
 * cannot be processed (listener throws an exception and retries are exhausted),
 * RabbitMQ automatically routes it to the DLQ instead of discarding it.
 * This prevents message loss and allows operators to inspect failed messages.
 *
 * <h2>Why declare topology in the consumer?</h2>
 * <p>In production, either the producer or a dedicated infrastructure-as-code
 * tool (Terraform, RabbitMQ management plugin) declares the topology. Declaring
 * it in the consumer ensures the application can start independently without
 * relying on the producer having run first.
 */
@Configuration
public class RabbitMQConfig {

    // ── Configuration values injected from application.yml ────────────────────────

    /**
     * Name of the direct exchange from which messages originate.
     * Injected from {@code spring.rabbitmq.exchange} in application.yml.
     */
    @Value("${spring.rabbitmq.exchange}")
    private String exchangeName;

    /**
     * Name of the durable queue this consumer listens on.
     * Injected from {@code spring.rabbitmq.queue} in application.yml.
     */
    @Value("${spring.rabbitmq.queue}")
    private String queueName;

    /**
     * The routing key that binds the queue to the exchange.
     * Injected from {@code spring.rabbitmq.routing-key}.
     */
    @Value("${spring.rabbitmq.routing-key}")
    private String routingKey;

    /**
     * Name of the dead-letter exchange where failed messages are routed.
     * Injected from {@code spring.rabbitmq.dead-letter-exchange}.
     */
    @Value("${spring.rabbitmq.dead-letter-exchange}")
    private String deadLetterExchangeName;

    /**
     * Name of the dead-letter queue that holds messages that could not be processed.
     * Injected from {@code spring.rabbitmq.dead-letter-queue}.
     */
    @Value("${spring.rabbitmq.dead-letter-queue}")
    private String deadLetterQueueName;

    // ── Exchange ──────────────────────────────────────────────────────────────────

    /**
     * Declare the durable {@link DirectExchange} that the producer publishes to.
     *
     * <p>Because RabbitMQ exchange/queue declarations are idempotent, declaring
     * this exchange here (as the producer also does) is safe: if it already
     * exists with the same settings, the declaration is a no-op.
     *
     * @return the configured {@link DirectExchange} bean
     */
    @Bean
    public DirectExchange ordersExchange() {
        // DirectExchange(name, durable, autoDelete)
        return new DirectExchange(exchangeName, true, false);
    }

    // ── Dead Letter Exchange ───────────────────────────────────────────────────────

    /**
     * Declare a dead-letter exchange (DLX) for messages that cannot be processed.
     *
     * <p>A <em>dead-letter exchange</em> is a standard RabbitMQ exchange that
     * receives messages rejected or expired from another queue. By using a
     * separate exchange for dead-letters we keep the DLQ topology independent
     * from the main routing topology, making it easier to wire additional queues
     * or alternate consumers to the DLX in the future.
     *
     * @return the dead-letter {@link DirectExchange} bean
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(deadLetterExchangeName, true, false);
    }

    // ── Queues ────────────────────────────────────────────────────────────────────

    /**
     * Declare the main durable queue with a dead-letter exchange configured.
     *
     * <p>The {@code x-dead-letter-exchange} argument tells RabbitMQ: "when a
     * message in this queue is rejected, expired, or exceeds the max-retry count,
     * forward it to the dead-letter exchange instead of discarding it."
     *
     * <p>The {@code x-dead-letter-routing-key} overrides the original routing key
     * when the message is re-published to the DLX, so it lands in the DLQ.
     *
     * @return the main orders {@link Queue} bean
     */
    @Bean
    public Queue ordersQueue() {
        return QueueBuilder.durable(queueName)
                // Route rejected/expired messages to the dead-letter exchange
                .withArgument("x-dead-letter-exchange", deadLetterExchangeName)
                // Use the DLQ name as the routing key when forwarding to the DLX
                .withArgument("x-dead-letter-routing-key", deadLetterQueueName)
                .build();
    }

    /**
     * Declare the dead-letter queue (DLQ) that holds failed messages.
     *
     * <p>This is a plain durable queue. Messages land here when they cannot
     * be processed by the main listener. Operators can inspect these messages
     * via the RabbitMQ Management UI or re-queue them after fixing the issue.
     *
     * @return the DLQ {@link Queue} bean
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueueName).build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────────

    /**
     * Bind the main orders queue to the orders exchange with the configured routing key.
     *
     * @param ordersQueue    the main queue bean
     * @param ordersExchange the direct exchange bean
     * @return the {@link Binding} linking the queue to the exchange
     */
    @Bean
    public Binding ordersBinding(Queue ordersQueue, DirectExchange ordersExchange) {
        return BindingBuilder
                .bind(ordersQueue)
                .to(ordersExchange)
                .with(routingKey);
    }

    /**
     * Bind the DLQ to the dead-letter exchange.
     *
     * <p>The binding key matches the {@code x-dead-letter-routing-key} set on
     * the main queue, so dead-lettered messages are routed here automatically.
     *
     * @param deadLetterQueue    the DLQ bean
     * @param deadLetterExchange the dead-letter exchange bean
     * @return the {@link Binding} linking the DLQ to the DLX
     */
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(deadLetterQueueName);
    }

    // ── Message Converter ─────────────────────────────────────────────────────────

    /**
     * Configure a JSON message converter for the AMQP infrastructure.
     *
     * <p>This converter is used in two directions:
     * <ul>
     *   <li><strong>Inbound</strong>: deserialises the JSON payload from RabbitMQ
     *       into a {@link com.example.rabbitmqconsumer.domain.OrderMessage} Java
     *       object before the {@code @RabbitListener} method is invoked.</li>
     *   <li><strong>Outbound</strong> (via {@link RabbitTemplate}): used in tests
     *       to publish messages in JSON format so the listener can deserialise them.</li>
     * </ul>
     *
     * @return the {@link MessageConverter} bean
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configure the {@link RabbitTemplate} with the JSON converter.
     *
     * <p>This template is used by integration tests to publish messages to the
     * queue. Using the same JSON converter ensures test messages match the format
     * the listener expects.
     *
     * @param connectionFactory the auto-configured AMQP connection factory
     * @return the configured {@link RabbitTemplate} bean
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // Use JSON serialisation so test-published messages match the listener's expectation
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
