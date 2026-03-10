package com.example.activemqjms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * JMS infrastructure configuration for the ActiveMQ integration.
 *
 * <p>This class wires together the key JMS beans required by both the producer
 * ({@link com.example.activemqjms.service.MessageProducerService}) and the consumer
 * ({@link com.example.activemqjms.service.MessageConsumerService}).
 *
 * <h2>How Spring Boot auto-configuration interacts with this class</h2>
 * <p>Because {@code spring-boot-starter-activemq} is on the classpath, Spring Boot
 * already creates a {@link jakarta.jms.ConnectionFactory} backed by ActiveMQ. This
 * class <em>extends</em> that auto-configuration by registering a JSON message
 * converter and a properly-configured listener container factory.
 *
 * <h2>Why JSON serialisation?</h2>
 * <p>By default, Spring JMS serialises message payloads using Java object serialisation,
 * which produces binary, non-human-readable messages and couples producers and consumers
 * to the same JVM class path. Using {@link MappingJackson2MessageConverter} instead
 * produces text messages with JSON content, which are:
 * <ul>
 *   <li>Human-readable in the ActiveMQ Web Console.</li>
 *   <li>Interoperable with consumers written in any language that understands JSON.</li>
 *   <li>Easier to inspect and replay during incident analysis.</li>
 * </ul>
 *
 * <h2>Why {@code @EnableJms}?</h2>
 * <p>{@link EnableJms} activates the Spring JMS annotation-driven listener infrastructure,
 * which scans the application context for beans annotated with
 * {@link org.springframework.jms.annotation.JmsListener} and registers them as
 * asynchronous message listeners against the broker.
 */
@Configuration
@EnableJms
public class JmsConfig {

    // ── Jackson ObjectMapper ──────────────────────────────────────────────────────

    /**
     * Configure a custom {@link ObjectMapper} with Java 8 date/time support.
     *
     * <p>The {@link JavaTimeModule} is needed so that {@link java.time.Instant}
     * (used in {@link com.example.activemqjms.domain.OrderMessage#getCreatedAt()})
     * is serialised as an ISO-8601 string rather than a numeric timestamp array.
     *
     * <p>{@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} must be
     * disabled explicitly to force string output; the default is enabled.
     *
     * @return a fully configured {@link ObjectMapper} bean
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register the module that handles Java 8 date/time types (Instant, LocalDate, etc.)
        mapper.registerModule(new JavaTimeModule());
        // Write Instant as "2024-03-15T10:15:30Z" instead of [1710495330, 0]
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ── Message Converter ─────────────────────────────────────────────────────────

    /**
     * Configure a JSON message converter for the JMS infrastructure.
     *
     * <p>{@link MappingJackson2MessageConverter} is the Spring JMS equivalent of
     * the AMQP {@code Jackson2JsonMessageConverter}. It:
     * <ul>
     *   <li>Serialises Java objects to {@link MessageType#TEXT} JMS messages with
     *       a JSON body.</li>
     *   <li>Adds a {@code _type} header to the JMS message carrying the fully
     *       qualified class name, allowing consumers to deserialise back to the
     *       correct Java type.</li>
     * </ul>
     *
     * <p>The {@code typeIdPropertyName} is the JMS property name that carries the
     * type information. Using {@code "_type"} is a common Spring convention.
     *
     * @return the {@link MessageConverter} bean used by {@link JmsTemplate} and listeners
     */
    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        // Use TEXT_MESSAGE (JSON string) instead of BYTES_MESSAGE (binary)
        converter.setTargetType(MessageType.TEXT);
        // The JMS property that carries the fully qualified Java class name for deserialisation
        converter.setTypeIdPropertyName("_type");
        // Use our custom ObjectMapper that supports Java 8 date/time types
        converter.setObjectMapper(objectMapper());
        return converter;
    }

    // ── JmsTemplate ───────────────────────────────────────────────────────────────

    /**
     * Configure the {@link JmsTemplate} to use the JSON message converter.
     *
     * <p>{@link JmsTemplate} is the primary Spring JMS class for sending messages
     * synchronously. The auto-configured instance is retrieved here via the
     * {@link ConnectionFactory} and augmented with the JSON converter.
     *
     * <p>Note: {@link JmsTemplate} is not thread-safe by design, but Spring Boot's
     * auto-configured instance is safe to share across threads because it uses
     * a thread-local session cache.
     *
     * @param connectionFactory the auto-configured JMS connection factory (ActiveMQ)
     * @return the configured {@link JmsTemplate} bean
     */
    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        // Use JSON serialisation instead of Java object serialisation
        template.setMessageConverter(jacksonJmsMessageConverter());
        return template;
    }

    // ── Listener Container Factory ─────────────────────────────────────────────────

    /**
     * Configure the {@link DefaultJmsListenerContainerFactory} used by
     * {@link org.springframework.jms.annotation.JmsListener} methods.
     *
     * <p>Spring Boot's auto-configured factory does not include our custom JSON
     * message converter, so we must override the factory bean to inject it.
     * Without this, the consumer would fail to deserialise incoming JSON messages
     * back into {@link com.example.activemqjms.domain.OrderMessage} objects.
     *
     * <p>The factory name {@code "jmsListenerContainerFactory"} is the conventional
     * default that {@code @JmsListener} looks for unless a different factory is specified.
     *
     * @param connectionFactory the auto-configured JMS connection factory (ActiveMQ)
     * @return the configured {@link DefaultJmsListenerContainerFactory} bean
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // Inject the JSON converter so that listener methods receive Java objects, not raw JMS messages
        factory.setMessageConverter(jacksonJmsMessageConverter());
        // Use auto-acknowledge mode: the container automatically acknowledges messages
        // after the listener method returns without throwing an exception.
        // If the listener throws, the message is redelivered (at-least-once semantics).
        factory.setSessionAcknowledgeMode(jakarta.jms.Session.AUTO_ACKNOWLEDGE);
        return factory;
    }
}
