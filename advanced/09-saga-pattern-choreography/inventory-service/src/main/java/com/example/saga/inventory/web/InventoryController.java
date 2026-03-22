package com.example.saga.inventory.web;

import com.example.saga.inventory.domain.ProductStock;
import com.example.saga.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST controller for the Inventory Service.
 *
 * <p>Exposes read endpoints for observability:
 * <ul>
 *   <li>{@code GET /api/inventory/{productId}} — current stock level for a product.</li>
 *   <li>{@code POST /api/inventory/{productId}/seed} — seed/reset stock for a product
 *       (useful for demo resets without restarting the service).</li>
 * </ul>
 *
 * <p>Stock reservations are handled automatically via Kafka events, not via REST.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Returns the current stock level for a product.
     *
     * @param productId the product identifier
     * @return 200 with stock details, or 404 if the product has never been seen
     */
    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getStock(@PathVariable String productId) {
        return inventoryService.findStock(productId)
                .map(StockResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Seeds initial stock for a product (creates with 10 units if not present).
     *
     * <p>Useful for demo resets — call this before placing orders to ensure
     * the product has stock available.
     *
     * @param productId the product to seed
     * @return 200 with the resulting stock record
     */
    @PostMapping("/{productId}/seed")
    public ResponseEntity<StockResponse> seedStock(@PathVariable String productId) {
        ProductStock stock = inventoryService.getOrCreateStock(productId);
        return ResponseEntity.ok(StockResponse.from(stock));
    }

    // -------------------------------------------------------------------------
    // Response DTO
    // -------------------------------------------------------------------------

    /**
     * Response body for stock queries.
     */
    public record StockResponse(
            String productId,
            int availableQuantity,
            String updatedAt
    ) {
        public static StockResponse from(ProductStock stock) {
            return new StockResponse(
                    stock.getProductId(),
                    stock.getAvailableQuantity(),
                    stock.getUpdatedAt().toString()
            );
        }
    }
}
