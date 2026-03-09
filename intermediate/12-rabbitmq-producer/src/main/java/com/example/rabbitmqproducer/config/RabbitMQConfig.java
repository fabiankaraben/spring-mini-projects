package com.example.rabbitmqproducer.config;

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
 * RabbitMQ topology and infrastructure configuration.
 *
 * <p>Spring AMQP uses the concept of <em>auto-declare</em>: every
 * {@link Queue}, {@link org.springframework.amqp.core.Exchange}, and
 * {@link Binding} bean declared here is automatically created in RabbitMQ
 * the first time the application connects to the broker. If they already
 * exist with compatible settings, RabbitMQ skips the creation silently.
 *
 * <h2>RabbitMQ topology created by this configuration</h2>
 * <pre>
 *   HTTP Client
 *       │  POST /api/messages
 *       ▼
 *   [Spring Boot Producer]
 *       │  RabbitTemplate.convertAndSend(exchange, routingKey, message)
 *       ▼
 *   [orders.exchange]  ← Direct Exchange
 *       │  routing key = "orders.routing.key"
 *       ▼
 *   [orders.queue]     ← Durable Queue
 *       │
 *       ▼
 *   (future consumer reads messages from here)
 * </pre>
 *
 * <h2>Why a Direct Exchange?</h2>
 * <p>A <em>direct exchange</em> routes a message to all queues whose binding
 * key exactly matches the message's routing key. This is the simplest and most
 * common exchange type and is well suited for point-to-point messaging such as
 * a task/order queue.
 *
 * <h2>Why durable queues?</h2>
 * <p>A <em>durable</em> queue survives a RabbitMQ broker restart. Without
 * durability, all enqueued messages are lost when the broker restarts, which
 * is unacceptable for production workloads.
 */
@Configuration
public class RabbitMQConfig {

    // ── Configuration values injected from application.yml ────────────────────────

    /**
     * Name of the direct exchange to which messages are published.
     * Injected from {@code spring.rabbitmq.exchange} in application.yml.
     */
    @Value("${spring.rabbitmq.exchange}")
    private String exchangeName;

    /**
     * Name of the durable queue that will receive the routed messages.
     * Injected from {@code spring.rabbitmq.queue} in application.yml.
     */
    @Value("${spring.rabbitmq.queue}")
    private String queueName;

    /**
     * The routing key used to bind the queue to the exchange and to address
     * outgoing messages. Injected from {@code spring.rabbitmq.routing-key}.
     */
    @Value("${spring.rabbitmq.routing-key}")
    private String routingKey;

    // ── Exchange ──────────────────────────────────────────────────────────────────

    /**
     * Declare a durable {@link DirectExchange}.
     *
     * <p>A {@code DirectExchange} routes messages to queues based on an exact
     * match between the message's routing key and the queue's binding key.
     *
     * <p>{@code durable = true} means this exchange survives a broker restart.
     * {@code autoDelete = false} means it is not removed when the last binding
     * is removed.
     *
     * @return the configured {@link DirectExchange} bean
     */
    @Bean
    public DirectExchange ordersExchange() {
        // DirectExchange(name, durable, autoDelete)
        return new DirectExchange(exchangeName, true, false);
    }

    // ── Queue ─────────────────────────────────────────────────────────────────────

    /**
     * Declare a durable {@link Queue}.
     *
     * <p>Using {@link QueueBuilder#durable(String)} produces a queue with
     * {@code durable = true}, so messages survive a broker restart even if
     * they have not been consumed yet.
     *
     * @return the configured durable {@link Queue} bean
     */
    @Bean
    public Queue ordersQueue() {
        // QueueBuilder fluent API: durable() sets x-durable=true in the AMQP declaration
        return QueueBuilder.durable(queueName).build();
    }

    // ── Binding ───────────────────────────────────────────────────────────────────

    /**
     * Bind the queue to the exchange with the configured routing key.
     *
     * <p>The {@link Binding} tells RabbitMQ: "when a message arrives at
     * {@code ordersExchange} with routing key equal to {@code routingKey},
     * deliver it to {@code ordersQueue}."
     *
     * <p>{@link BindingBuilder} provides a fluent DSL that reads naturally:
     * {@code bind(queue).to(exchange).with(routingKey)}.
     *
     * @param ordersQueue    the queue bean declared above
     * @param ordersExchange the exchange bean declared above
     * @return the {@link Binding} that links them together
     */
    @Bean
    public Binding ordersBinding(Queue ordersQueue, DirectExchange ordersExchange) {
        return BindingBuilder
                .bind(ordersQueue)
                .to(ordersExchange)
                .with(routingKey);
    }

    // ── Message Converter ─────────────────────────────────────────────────────────

    /**
     * Configure a JSON message converter for the AMQP infrastructure.
     *
     * <p>By default, Spring AMQP serialises message payloads using Java
     * object serialisation, which produces non-human-readable binary messages.
     * {@link Jackson2JsonMessageConverter} serialises Java objects to JSON
     * using Jackson, producing messages that:
     * <ul>
     *   <li>Are human-readable in the RabbitMQ Management UI.</li>
     *   <li>Can be consumed by any language that understands JSON (Python,
     *       Node.js, Go, etc.).</li>
     *   <li>Carry standard {@code content_type: application/json} AMQP headers
     *       so consumers know how to deserialise the payload.</li>
     * </ul>
     *
     * @return the {@link MessageConverter} bean used by {@link RabbitTemplate}
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ── RabbitTemplate ────────────────────────────────────────────────────────────

    /**
     * Configure the {@link RabbitTemplate} to use the JSON message converter.
     *
     * <p>{@link RabbitTemplate} is the main Spring AMQP class for sending
     * messages. The auto-configured instance is retrieved here via the
     * {@link ConnectionFactory} and augmented with the JSON converter.
     *
     * <p>Using constructor injection through the bean method parameter ensures
     * the {@link ConnectionFactory} is fully initialised before the template
     * is created, avoiding race conditions during context startup.
     *
     * @param connectionFactory the auto-configured AMQP connection factory
     * @return the configured {@link RabbitTemplate} bean
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // Use JSON serialisation instead of Java object serialisation
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
