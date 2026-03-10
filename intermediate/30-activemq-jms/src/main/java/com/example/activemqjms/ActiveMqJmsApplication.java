package com.example.activemqjms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the ActiveMQ JMS mini-project.
 *
 * <p>{@link SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Configuration} – marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – tells Spring Boot to auto-configure
 *       the application based on the classpath. Because {@code spring-boot-starter-activemq}
 *       is on the classpath, Spring Boot auto-configures a {@link jakarta.jms.ConnectionFactory},
 *       a {@link org.springframework.jms.core.JmsTemplate}, and a
 *       {@link org.springframework.jms.config.DefaultJmsListenerContainerFactory}.</li>
 *   <li>{@code @ComponentScan} – scans this package and all sub-packages for
 *       Spring-managed components ({@code @Service}, {@code @Controller}, etc.).</li>
 * </ul>
 *
 * <h2>Architecture overview</h2>
 * <pre>
 *   HTTP Client
 *       │  POST /api/messages/orders
 *       ▼
 *   [MessageController]
 *       │  delegates to
 *       ▼
 *   [MessageProducerService]
 *       │  JmsTemplate.convertAndSend(destination, payload)
 *       ▼
 *   [ActiveMQ Broker]  ← queue: orders.queue
 *       │
 *       ▼
 *   [MessageConsumerService]  ← @JmsListener(destination = "orders.queue")
 *       │  processes the order message asynchronously
 *       ▼
 *   [OrderProcessingService]  ← domain logic layer
 * </pre>
 */
@SpringBootApplication
public class ActiveMqJmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActiveMqJmsApplication.class, args);
    }
}
