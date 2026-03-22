package com.example.saga.inventory.integration;

import com.example.saga.inventory.domain.ProductStock;
import com.example.saga.inventory.events.PaymentProcessedEvent;
import com.example.saga.inventory.repository.ProductStockRepository;
import com.example.saga.inventory.repository.ReservationRepository;
import com.example.saga.inventory.service.InventoryService;
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
 * Integration tests for the Inventory Service.
 *
 * <p>Uses real PostgreSQL and Kafka containers via Testcontainers to verify
 * that stock reservations and persistence work correctly end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Inventory Service Integration Tests")
class InventoryServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("inventorydb")
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
    private InventoryService inventoryService;

    @Autowired
    private ProductStockRepository stockRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("reserveInventory auto-seeds product with 10 units and reserves successfully")
    void reserveInventoryAutoSeedsAndReserves() {
        UUID orderId = UUID.randomUUID();
        PaymentProcessedEvent event = new PaymentProcessedEvent(
                orderId, "cust-int", UUID.randomUUID(), new BigDecimal("30.00"), "prod-integration", 3);

        inventoryService.reserveInventory(event);

        // Stock was seeded and decremented
        Optional<ProductStock> stock = stockRepository.findById("prod-integration");
        assertThat(stock).isPresent();
        assertThat(stock.get().getAvailableQuantity())
                .isEqualTo(ProductStock.INITIAL_STOCK - 3);

        // Reservation record was created
        assertThat(reservationRepository.findByOrderId(orderId)).isPresent();
        assertThat(reservationRepository.findByOrderId(orderId).get().isSuccessful()).isTrue();
    }

    @Test
    @DisplayName("reserveInventory creates FAILED reservation when quantity exceeds stock")
    void reserveInventoryCreatesFailedReservationWhenInsufficientStock() {
        // First drain all stock manually
        ProductStock stock = inventoryService.getOrCreateStock("prod-drain");
        stock.reserve(ProductStock.INITIAL_STOCK); // drain all 10
        stockRepository.save(stock);

        UUID orderId = UUID.randomUUID();
        PaymentProcessedEvent event = new PaymentProcessedEvent(
                orderId, "cust-fail", UUID.randomUUID(), new BigDecimal("30.00"), "prod-drain", 1);

        inventoryService.reserveInventory(event);

        // Reservation exists but is failed
        var reservation = reservationRepository.findByOrderId(orderId);
        assertThat(reservation).isPresent();
        assertThat(reservation.get().isSuccessful()).isFalse();
        assertThat(reservation.get().getFailureReason()).contains("Insufficient stock");

        // Stock remains at 0
        assertThat(stockRepository.findById("prod-drain").get().getAvailableQuantity()).isZero();
    }

    @Test
    @DisplayName("reserveInventory is idempotent — second call for same order does nothing")
    void reserveInventoryIsIdempotentIntegration() {
        UUID orderId = UUID.randomUUID();
        PaymentProcessedEvent event = new PaymentProcessedEvent(
                orderId, "cust-idem", UUID.randomUUID(), new BigDecimal("30.00"), "prod-idem", 2);

        inventoryService.reserveInventory(event);
        inventoryService.reserveInventory(event); // second call must be ignored

        // Only one reservation record
        assertThat(reservationRepository.findAll()).hasSize(1);

        // Stock only decremented once
        assertThat(stockRepository.findById("prod-idem").get().getAvailableQuantity())
                .isEqualTo(ProductStock.INITIAL_STOCK - 2);
    }

    @Test
    @DisplayName("getOrCreateStock returns same record on repeated calls")
    void getOrCreateStockIsIdempotent() {
        ProductStock first = inventoryService.getOrCreateStock("prod-repeat");
        ProductStock second = inventoryService.getOrCreateStock("prod-repeat");

        assertThat(second.getProductId()).isEqualTo(first.getProductId());
        assertThat(second.getAvailableQuantity()).isEqualTo(first.getAvailableQuantity());

        // Only one row in the database
        assertThat(stockRepository.findAll()
                .stream().filter(s -> "prod-repeat".equals(s.getProductId()))
                .count()).isEqualTo(1);
    }
}
