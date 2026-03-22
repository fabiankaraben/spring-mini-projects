package com.example.saga.order.config;

import com.example.saga.order.events.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for the Order Service.
 *
 * <p>Configures:
 * <ul>
 *   <li>A {@link KafkaTemplate} (producer) that serializes event objects to JSON.</li>
 *   <li>A {@link ConcurrentKafkaListenerContainerFactory} (consumer factory) that
 *       deserializes JSON messages back into the correct event record types.</li>
 * </ul>
 *
 * <p>Why explicit config instead of Spring Boot auto-configuration?
 *   We need to configure the JsonDeserializer trusted packages to allow
 *   deserialization of our custom event records (security feature of spring-kafka).
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // -------------------------------------------------------------------------
    // Producer configuration
    // -------------------------------------------------------------------------

    /**
     * Builds the Kafka producer configuration map.
     *
     * <p>Key settings:
     * <ul>
     *   <li>{@code KEY_SERIALIZER_CLASS_CONFIG} — orderId (String) used as the partition key.</li>
     *   <li>{@code VALUE_SERIALIZER_CLASS_CONFIG} — events serialized as JSON using Jackson.</li>
     * </ul>
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Key is a String (orderId) — ensures all events for the same order go to the same partition
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Value is a Java object serialized as JSON by Jackson
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Include type info in the JSON so the consumer knows which class to deserialize to
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * KafkaTemplate is the high-level API for publishing messages.
     * Spring injects it wherever {@code @Autowired KafkaTemplate<String, Object>} appears.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // -------------------------------------------------------------------------
    // Consumer configuration
    // -------------------------------------------------------------------------

    /**
     * Builds the Kafka consumer configuration map.
     *
     * <p>Key settings:
     * <ul>
     *   <li>{@code GROUP_ID_CONFIG} — consumer group. All Order Service instances
     *       share one group so each message is processed by exactly one instance.</li>
     *   <li>{@code VALUE_DESERIALIZER_CLASS_CONFIG} — JSON deserialization using Jackson.</li>
     *   <li>{@code TRUSTED_PACKAGES} — whitelists our event package to prevent injection attacks.</li>
     * </ul>
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service-group");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Only trust our own events package — defense against deserialization attacks
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.saga.*");
        // Use the type header set by the producer to choose the correct target class
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Container factory for {@code @KafkaListener}-annotated methods.
     *
     * <p>ConcurrentKafkaListenerContainerFactory allows multiple listener threads
     * (concurrency) to consume from the same topic in parallel.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
