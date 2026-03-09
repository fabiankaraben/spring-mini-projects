package com.example.kafkaconsumer.config;

import com.example.kafkaconsumer.domain.OrderEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration.
 *
 * <p>This class explicitly declares the {@link ConsumerFactory} and
 * {@link ConcurrentKafkaListenerContainerFactory} beans instead of relying
 * entirely on Spring Boot auto-configuration. Explicit configuration is used
 * here for educational purposes so every setting is visible and documented.
 *
 * <h2>Key concepts</h2>
 * <ul>
 *   <li><strong>ConsumerFactory</strong> – creates {@code KafkaConsumer} instances
 *       with the given properties. Spring Kafka's listener container asks the
 *       factory for a consumer on startup.</li>
 *   <li><strong>ConcurrentKafkaListenerContainerFactory</strong> – the container
 *       factory that Spring Kafka uses when it sees {@code @KafkaListener}
 *       methods. The factory name {@code "kafkaListenerContainerFactory"} is the
 *       default Spring Boot name; our {@code @KafkaListener} references it
 *       explicitly via {@code containerFactory = "kafkaListenerContainerFactory"}.</li>
 *   <li><strong>Manual acknowledgement</strong> – setting the ack mode to
 *       {@link ContainerProperties.AckMode#MANUAL_IMMEDIATE} means the consumer
 *       offset is committed only when the listener calls
 *       {@code Acknowledgment.acknowledge()}. This prevents silent message
 *       loss if the listener crashes after fetching but before processing.</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfig {

    /** Bootstrap address of the Kafka broker. */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /** Consumer group identifier. All consumers in the same group share the topic partitions. */
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Builds the property map that will be used to create {@code KafkaConsumer} instances.
     *
     * <p>Properties are the same ones you would pass to the Kafka client library
     * directly; Spring Kafka simply forwards them to the underlying consumer.
     *
     * @return a map of Kafka consumer configuration properties
     */
    private Map<String, Object> consumerProps() {
        Map<String, Object> props = new HashMap<>();

        // ── Connection ──────────────────────────────────────────────────────
        // Comma-separated list of host:port pairs to use for establishing the
        // initial connection to the Kafka cluster.
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // ── Consumer group ──────────────────────────────────────────────────
        // Consumers with the same group-id share partitions of the subscribed
        // topics. Each partition is consumed by exactly one consumer in the group.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // ── Offset reset ────────────────────────────────────────────────────
        // "earliest" – start from the beginning of the topic if no committed
        // offset exists for this group (useful for development and integration tests).
        // "latest"  – skip messages that arrived before this consumer started.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // ── Offset committing ────────────────────────────────────────────────
        // Disable auto-commit because we use MANUAL_IMMEDIATE ack mode.
        // Auto-commit would interfere with manual acknowledgement.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // ── Deserialisers ────────────────────────────────────────────────────
        // Key deserialiser: the message key is the orderId string.
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Value deserialiser: Spring Kafka's JsonDeserializer converts the JSON
        // bytes in the message value into an OrderEvent instance.
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Tell JsonDeserializer which Java class to deserialise the value into.
        // This is required when the producer does not embed type headers, or
        // when we want to override the embedded type for flexibility.
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderEvent.class.getName());

        // Allow JsonDeserializer to deserialise classes from this package.
        // This is a security measure: JsonDeserializer refuses to instantiate
        // classes that are not explicitly trusted to prevent deserialization attacks.
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.kafkaconsumer.domain");

        // When the producer embeds a type header (spring.json.add.type.headers=true),
        // setting this to false tells JsonDeserializer to use VALUE_DEFAULT_TYPE
        // instead of the embedded header. This decouples consumer from producer class names.
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return props;
    }

    /**
     * Creates the {@link ConsumerFactory} bean.
     *
     * <p>The factory is parameterised with {@code <String, OrderEvent>}:
     * <ul>
     *   <li>{@code String}     – the message key type (orderId)</li>
     *   <li>{@code OrderEvent} – the message value type</li>
     * </ul>
     *
     * @return a configured {@link DefaultKafkaConsumerFactory}
     */
    @Bean
    public ConsumerFactory<String, OrderEvent> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProps());
    }

    /**
     * Creates the {@link ConcurrentKafkaListenerContainerFactory} bean.
     *
     * <p>This factory is used by Spring Kafka when it processes all
     * {@code @KafkaListener} annotations in the application. It wraps the
     * {@link ConsumerFactory} and adds container-level settings such as
     * the acknowledgement mode.
     *
     * <p>The bean name {@code "kafkaListenerContainerFactory"} is the Spring Boot
     * default name. It must match the {@code containerFactory} attribute in the
     * {@code @KafkaListener} annotation in
     * {@link com.example.kafkaconsumer.listener.OrderEventListener}.
     *
     * @return a configured {@link ConcurrentKafkaListenerContainerFactory}
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        // Use the consumer factory we configured above
        factory.setConsumerFactory(consumerFactory());

        // MANUAL_IMMEDIATE: the listener must call Acknowledgment.acknowledge()
        // to commit the offset. The commit happens immediately when acknowledge() is called.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}
