package com.example.saga.inventory.service;

import com.example.saga.inventory.domain.ProductStock;
import com.example.saga.inventory.domain.Reservation;
import com.example.saga.inventory.events.InventoryFailedEvent;
import com.example.saga.inventory.events.InventoryReservedEvent;
import com.example.saga.inventory.events.PaymentProcessedEvent;
import com.example.saga.inventory.repository.ProductStockRepository;
import com.example.saga.inventory.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InventoryService}.
 *
 * <p>Verifies the stock reservation logic in isolation with Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService unit tests")
class InventoryServiceTest {

    @Mock
    private ProductStockRepository stockRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(stockRepository, reservationRepository, kafkaTemplate);
    }

    // =========================================================================
    // reserveInventory — happy path
    // =========================================================================

    @Test
    @DisplayName("reserveInventory publishes InventoryReservedEvent when stock is sufficient")
    void reserveInventoryPublishesReservedEvent() {
        UUID orderId = UUID.randomUUID();
        String productId = "prod-X";
        int quantity = 3;

        // Build a PaymentProcessedEvent — this record needs productId and quantity
        // Those are added to the inventory service's PaymentProcessedEvent below
        PaymentProcessedEvent event = buildEvent(orderId, productId, quantity);

        // No prior reservation
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        // Stock available
        ProductStock stock = new ProductStock(productId);
        when(stockRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Reservation saved with UUID id
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            try {
                var f = Reservation.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(r, UUID.randomUUID());
            } catch (Exception e) { throw new RuntimeException(e); }
            return r;
        });

        inventoryService.reserveInventory(event);

        // Verify InventoryReservedEvent published
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(InventoryService.TOPIC_INVENTORY_RESERVED), anyString(), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InventoryReservedEvent.class);

        InventoryReservedEvent published = (InventoryReservedEvent) captor.getValue();
        assertThat(published.orderId()).isEqualTo(orderId);
        assertThat(published.productId()).isEqualTo(productId);
        assertThat(published.quantity()).isEqualTo(quantity);

        // Stock decremented
        assertThat(stock.getAvailableQuantity()).isEqualTo(ProductStock.INITIAL_STOCK - quantity);

        // No failure event
        verify(kafkaTemplate, never()).send(eq(InventoryService.TOPIC_INVENTORY_FAILED), anyString(), any());
    }

    @Test
    @DisplayName("reserveInventory publishes InventoryFailedEvent when stock insufficient")
    void reserveInventoryFailsWhenInsufficientStock() {
        UUID orderId = UUID.randomUUID();
        String productId = "prod-Y";
        int quantity = 15; // More than INITIAL_STOCK (10)

        PaymentProcessedEvent event = buildEvent(orderId, productId, quantity);

        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        ProductStock stock = new ProductStock(productId);
        when(stockRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.reserveInventory(event);

        // Verify InventoryFailedEvent published
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(InventoryService.TOPIC_INVENTORY_FAILED), anyString(), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InventoryFailedEvent.class);

        InventoryFailedEvent failed = (InventoryFailedEvent) captor.getValue();
        assertThat(failed.orderId()).isEqualTo(orderId);
        assertThat(failed.productId()).isEqualTo(productId);
        assertThat(failed.reason()).contains("Insufficient stock");

        // Stock NOT changed on failure
        assertThat(stock.getAvailableQuantity()).isEqualTo(ProductStock.INITIAL_STOCK);

        // No success event
        verify(kafkaTemplate, never()).send(eq(InventoryService.TOPIC_INVENTORY_RESERVED), anyString(), any());
    }

    @Test
    @DisplayName("reserveInventory is idempotent — skips duplicate PaymentProcessedEvent")
    void reserveInventoryIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        PaymentProcessedEvent event = buildEvent(orderId, "prod-Z", 2);

        // Simulate existing reservation
        Reservation existing = new Reservation(orderId, "prod-Z", 2, true, null);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        inventoryService.reserveInventory(event);

        // No stock access, no Kafka event
        verify(stockRepository, never()).findByIdForUpdate(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    // =========================================================================
    // getOrCreateStock
    // =========================================================================

    @Test
    @DisplayName("getOrCreateStock creates new stock when product is unknown")
    void getOrCreateStockCreatesNew() {
        String productId = "new-prod";
        when(stockRepository.findByIdForUpdate(productId)).thenReturn(Optional.empty());
        when(stockRepository.save(any(ProductStock.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductStock result = inventoryService.getOrCreateStock(productId);

        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getAvailableQuantity()).isEqualTo(ProductStock.INITIAL_STOCK);
        verify(stockRepository).save(any(ProductStock.class));
    }

    @Test
    @DisplayName("getOrCreateStock returns existing stock when product is known")
    void getOrCreateStockReturnsExisting() {
        String productId = "existing-prod";
        ProductStock existing = new ProductStock(productId);
        when(stockRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(existing));

        ProductStock result = inventoryService.getOrCreateStock(productId);

        assertThat(result).isSameAs(existing);
        verify(stockRepository, never()).save(any());
    }

    // =========================================================================
    // Helper
    // =========================================================================

    /**
     * Builds a PaymentProcessedEvent for the inventory package.
     * The inventory package's event includes productId and quantity.
     */
    private PaymentProcessedEvent buildEvent(UUID orderId, String productId, int quantity) {
        return new PaymentProcessedEvent(orderId, "cust-1", UUID.randomUUID(),
                new BigDecimal("30.00"), productId, quantity);
    }
}
