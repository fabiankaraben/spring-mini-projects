package com.example.kafkaproducer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration.
 *
 * <p>Declaring a {@link NewTopic} bean tells Spring Kafka's {@code KafkaAdmin}
 * to automatically create the topic when the application starts (if it does
 * not already exist). This removes the need for manual topic creation via the
 * Kafka CLI before running the application.
 *
 * <p>Configuration values are externalised to {@code application.yml} so the
 * topic name, partition count, and replication factor can be adjusted without
 * touching the source code.
 */
@Configuration
public class KafkaTopicConfig {

    /** Name of the Kafka topic where order events will be published. */
    @Value("${app.kafka.topic.orders}")
    private String ordersTopic;

    /** Number of partitions for the topic. */
    @Value("${app.kafka.topic.partitions:3}")
    private int partitions;

    /**
     * Replication factor for the topic.
     *
     * <p>A factor of 1 is acceptable for local / single-broker development.
     * Production clusters should use at least 3.
     */
    @Value("${app.kafka.topic.replication-factor:1}")
    private short replicationFactor;

    /**
     * Declares the orders topic bean.
     *
     * <p>{@link TopicBuilder} is a Spring Kafka fluent builder that generates
     * the Kafka Admin API call needed to create the topic with the given
     * partition count and replication factor.
     *
     * @return a {@link NewTopic} descriptor that KafkaAdmin will use to
     *         provision the topic on first application startup.
     */
    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name(ordersTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}
