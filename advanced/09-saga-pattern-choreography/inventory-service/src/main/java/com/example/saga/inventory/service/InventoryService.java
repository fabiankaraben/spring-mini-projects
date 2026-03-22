package com.example.saga.inventory.service;

import com.example.saga.inventory.domain.ProductStock;
import com.example.saga.inventory.domain.Reservation;
import com.example.saga.inventory.events.InventoryFailedEvent;
import com.example.saga.inventory.events.InventoryReservedEvent;
import com.example.saga.inventory.events.PaymentProcessedEvent;
import com.example.saga.inventory.repository.ProductStockRepository;
import com.example.saga.inventory.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Core business logic for the Inventory Service.
 *
 * <p>Inventory simulation rule:
 * <ul>
 *   <li>Each product is auto-seeded with {@link ProductStock#INITIAL_STOCK} (10) units
 *       on first encounter.</li>
 *   <li>A reservation succeeds if {@code availableQuantity >= requestedQuantity}.</li>
 *   <li>A reservation fails if {@code availableQuantity < requestedQuantity}.</li>
 * </ul>
 *
 * <p>Concurrency safety:
 *   The stock row is locked via a pessimistic write lock ({@code SELECT FOR UPDATE})
 *   before attempting a reservation, preventing overselling under concurrent load.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** Kafka topic constants to prevent typos. */
    public static final String TOPIC_INVENTORY_RESERVED = "inventory.reserved";
    public static final String TOPIC_INVENTORY_FAILED   = "inventory.failed";

    private final ProductStockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryService(ProductStockRepository stockRepository,
                            ReservationRepository reservationRepository,
                            KafkaTemplate<String, Object> kafkaTemplate) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Attempts to reserve inventory for the given order.
     *
     * <ol>
     *   <li>Idempotency check — skip if a reservation already exists for this order.</li>
     *   <li>Load or create the {@link ProductStock} record with a pessimistic lock.</li>
     *   <li>Call {@link ProductStock#reserve(int)} to attempt the deduction.</li>
     *   <li>Persist the result and publish the appropriate Kafka event.</li>
     * </ol>
     *
     * @param event the PaymentProcessedEvent triggering the reservation
     */
    @Transactional
    public void reserveInventory(PaymentProcessedEvent event) {
        log.info("Reserving inventory for orderId={}", event.orderId());

        // Idempotency: skip if already processed
        Optional<Reservation> existing = reservationRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            log.warn("Duplicate PaymentProcessedEvent for orderId={} — skipping", event.orderId());
            return;
        }

        // Load or create stock record (with pessimistic lock to prevent concurrent overselling)
        ProductStock stock = getOrCreateStock(event.productId());

        // Attempt reservation
        boolean reserved = stock.reserve(event.quantity());
        stockRepository.save(stock);

        if (reserved) {
            // Happy path: save successful reservation record and publish event
            Reservation reservation = new Reservation(
                    event.orderId(), event.productId(), event.quantity(), true, null);
            reservation = reservationRepository.save(reservation);

            InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                    event.orderId(), event.productId(), event.quantity(), reservation.getId());
            kafkaTemplate.send(TOPIC_INVENTORY_RESERVED, event.orderId().toString(), reservedEvent);
            log.info("Inventory RESERVED: orderId={}, product={}, qty={}",
                    event.orderId(), event.productId(), event.quantity());

        } else {
            // Failure path: save failed reservation record and publish failure event
            String reason = String.format("Insufficient stock for product '%s': requested %d, available %d",
                    event.productId(), event.quantity(), stock.getAvailableQuantity() + event.quantity());
            Reservation reservation = new Reservation(
                    event.orderId(), event.productId(), event.quantity(), false, reason);
            reservationRepository.save(reservation);

            InventoryFailedEvent failedEvent = new InventoryFailedEvent(
                    event.orderId(), event.productId(), reason);
            kafkaTemplate.send(TOPIC_INVENTORY_FAILED, event.orderId().toString(), failedEvent);
            log.info("Inventory FAILED: orderId={}, reason={}", event.orderId(), reason);
        }
    }

    /**
     * Returns or creates the stock record for a product.
     *
     * <p>Uses a pessimistic write lock for the read path to ensure correctness
     * under concurrent requests. On first encounter, a new stock record with
     * {@link ProductStock#INITIAL_STOCK} units is created and saved.
     *
     * @param productId the product identifier
     * @return the stock record (locked for update within the current transaction)
     */
    @Transactional
    public ProductStock getOrCreateStock(String productId) {
        return stockRepository.findByIdForUpdate(productId)
                .orElseGet(() -> {
                    log.info("Auto-seeding stock for new product '{}' with {} units",
                            productId, ProductStock.INITIAL_STOCK);
                    return stockRepository.save(new ProductStock(productId));
                });
    }

    /**
     * Returns the current stock level for a product (read-only).
     *
     * @param productId the product identifier
     * @return an Optional containing the stock record if it exists
     */
    @Transactional(readOnly = true)
    public Optional<ProductStock> findStock(String productId) {
        return stockRepository.findById(productId);
    }
}
