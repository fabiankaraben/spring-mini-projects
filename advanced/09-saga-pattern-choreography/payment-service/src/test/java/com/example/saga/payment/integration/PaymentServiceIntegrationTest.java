package com.example.saga.payment.integration;

import com.example.saga.payment.domain.Payment;
import com.example.saga.payment.domain.PaymentStatus;
import com.example.saga.payment.events.OrderCreatedEvent;
import com.example.saga.payment.repository.PaymentRepository;
import com.example.saga.payment.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Payment Service.
 *
 * <p>Uses real PostgreSQL and Kafka containers via Testcontainers to verify
 * that payment processing and persistence work correctly end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Payment Service Integration Tests")
class PaymentServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("paymentdb")
                    .withUsername("saga")
                    .withPassword("saga");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("processPayment persists COMPLETED payment for even total price in real PostgreSQL")
    void processPaymentPersistsCompletedForEvenTotal() {
        // $30.00 — even → should succeed
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, "cust-integration", "prod-1", 3, new BigDecimal("30.00"));

        paymentService.processPayment(event);

        Optional<Payment> found = paymentRepository.findByOrderId(orderId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(found.get().getAmount()).isEqualByComparingTo("30.00");
        assertThat(found.get().getCustomerId()).isEqualTo("cust-integration");
    }

    @Test
    @DisplayName("processPayment persists FAILED payment for odd total price in real PostgreSQL")
    void processPaymentPersistsFailedForOddTotal() {
        // $29.99 — odd → should fail
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, "cust-integration-fail", "prod-2", 1, new BigDecimal("29.99"));

        paymentService.processPayment(event);

        Optional<Payment> found = paymentRepository.findByOrderId(orderId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(found.get().getReason()).contains("odd total price");
    }

    @Test
    @DisplayName("processPayment is idempotent — second call for same order does nothing")
    void processPaymentIsIdempotentIntegration() {
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, "cust-idem", "prod-3", 2, new BigDecimal("20.00"));

        paymentService.processPayment(event);
        paymentService.processPayment(event); // second call must be ignored

        // Only one payment record should exist
        assertThat(paymentRepository.findAll()).hasSize(1);
    }
}
