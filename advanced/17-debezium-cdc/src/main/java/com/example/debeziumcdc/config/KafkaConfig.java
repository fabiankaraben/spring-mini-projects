package com.example.debeziumcdc.config;

import com.example.debeziumcdc.cdc.ProductCdcEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * Kafka producer and consumer configuration for the Debezium CDC mini-project.
 *
 * <p>Two beans are configured here:
 * <ol>
 *   <li><strong>Producer</strong> — {@link KafkaTemplate} that publishes
 *       {@link ProductCdcEvent} objects to the {@code product-cdc-events} topic.
 *       Uses {@link JsonSerializer} to serialize the event as JSON.</li>
 *   <li><strong>Consumer</strong> — {@link ConcurrentKafkaListenerContainerFactory}
 *       that drives the {@link com.example.debeziumcdc.cdc.CdcEventConsumer} listener.
 *       Uses {@link JsonDeserializer} to deserialize messages back into
 *       {@link ProductCdcEvent} objects.</li>
 * </ol>
 *
 * <p>A shared {@link ObjectMapper} bean is also provided, configured with:
 * <ul>
 *   <li>{@link JavaTimeModule} — for serializing {@link java.time.Instant} as ISO-8601 strings.</li>
 *   <li>{@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} disabled — human-readable dates.</li>
 * </ul>
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // -------------------------------------------------------------------------
    // Shared ObjectMapper
    // -------------------------------------------------------------------------

    /**
     * A Jackson {@link ObjectMapper} configured for this application.
     *
     * <p>Registers {@link JavaTimeModule} so that Java 8 date/time types
     * (like {@link java.time.Instant}) serialize as ISO-8601 strings
     * instead of numeric arrays.
     *
     * <p>This bean is also auto-wired by Spring MVC (for REST serialization)
     * and by {@link com.example.debeziumcdc.cdc.CdcEventDispatcher}
     * (for parsing Debezium JSON envelopes).
     *
     * @return configured ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register the Java 8 date/time module (Instant, LocalDate, etc.)
        mapper.registerModule(new JavaTimeModule());
        // Serialize Instant as "2024-01-15T12:00:00Z" not as [2024, 1, 15, ...]
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // -------------------------------------------------------------------------
    // Kafka Producer
    // -------------------------------------------------------------------------

    /**
     * Kafka producer factory that creates {@link org.apache.kafka.clients.producer.KafkaProducer}
     * instances configured to serialize {@link ProductCdcEvent} as JSON.
     *
     * <p>Key producer settings:
     * <ul>
     *   <li>{@code BOOTSTRAP_SERVERS_CONFIG} — broker address(es).</li>
     *   <li>{@code KEY_SERIALIZER_CLASS_CONFIG} — product ID as a string key.</li>
     *   <li>{@code VALUE_SERIALIZER_CLASS_CONFIG} — event body as JSON.</li>
     *   <li>{@code ADD_TYPE_INFO_HEADERS_CONFIG=false} — do not add type headers;
     *       the consumer uses trusted packages instead.</li>
     * </ul>
     *
     * @return the producer factory
     */
    @Bean
    public ProducerFactory<String, ProductCdcEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Do NOT add Spring's __TypeId__ header — we use trusted packages on the consumer side.
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * {@link KafkaTemplate} used by {@link com.example.debeziumcdc.cdc.CdcEventDispatcher}
     * to publish {@link ProductCdcEvent} records to Kafka.
     *
     * @return the KafkaTemplate
     */
    @Bean
    public KafkaTemplate<String, ProductCdcEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // -------------------------------------------------------------------------
    // Kafka Consumer
    // -------------------------------------------------------------------------

    /**
     * Kafka consumer factory that creates {@link org.apache.kafka.clients.consumer.KafkaConsumer}
     * instances configured to deserialize JSON messages back into {@link ProductCdcEvent} objects.
     *
     * <p>Key consumer settings:
     * <ul>
     *   <li>{@code GROUP_ID_CONFIG} — consumer group for the CDC event consumer.</li>
     *   <li>{@code AUTO_OFFSET_RESET_CONFIG=earliest} — start from the earliest available
     *       message when the consumer group is first registered (important for integration tests).</li>
     *   <li>{@code TRUSTED_PACKAGES} — Jackson trusted package list for deserialization.
     *       Only classes in trusted packages are instantiated during JSON deserialization,
     *       preventing deserialization attacks.</li>
     * </ul>
     *
     * @return the consumer factory
     */
    @Bean
    public ConsumerFactory<String, ProductCdcEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "cdc-event-consumer-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Trust all classes in the application package for deserialization
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.debeziumcdc.*");
        // Tell the deserializer which class to deserialize to
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ProductCdcEvent.class.getName());
        // Do not use type headers — rely on VALUE_DEFAULT_TYPE instead
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * {@link ConcurrentKafkaListenerContainerFactory} that powers the
     * {@code @KafkaListener} in {@link com.example.debeziumcdc.cdc.CdcEventConsumer}.
     *
     * <p>Concurrency is set to 1 (single thread) because we only have one topic
     * partition by default. Increase this to match the number of topic partitions
     * for parallel consumption.
     *
     * @return the listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProductCdcEvent>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProductCdcEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
