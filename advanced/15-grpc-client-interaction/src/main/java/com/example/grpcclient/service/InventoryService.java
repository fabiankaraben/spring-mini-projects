package com.example.grpcclient.service;

import com.example.grpcclient.domain.InventoryItem;
import com.example.grpcclient.repository.InventoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Inventory domain logic.
 *
 * <p>This class encapsulates all business rules for inventory management.
 * It operates exclusively on JPA {@link InventoryItem} entities, with no
 * dependency on protobuf types (that translation lives in the gRPC layer).
 *
 * <p>Business rules enforced here:
 * <ul>
 *   <li>Reservations succeed only when sufficient available stock exists.</li>
 *   <li>Releases cannot exceed the currently reserved quantity.</li>
 *   <li>Available quantity is always computed as (total - reserved).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class InventoryService {

    /**
     * Spring Data JPA repository for CRUD operations on inventory items.
     */
    private final InventoryItemRepository inventoryItemRepository;

    /**
     * Constructor injection — ensures the dependency is required and immutable.
     *
     * @param inventoryItemRepository the JPA repository for inventory items
     */
    public InventoryService(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /**
     * Find an inventory item by its SKU.
     *
     * @param sku the product SKU to look up
     * @return Optional containing the item if found, empty if not
     */
    public Optional<InventoryItem> findBySku(String sku) {
        return inventoryItemRepository.findById(sku);
    }

    /**
     * Retrieve all inventory items.
     *
     * @param onlyAvailable if true, return only items with available quantity > 0
     * @return list of matching inventory items
     */
    public List<InventoryItem> findAll(boolean onlyAvailable) {
        if (onlyAvailable) {
            // Return only items with positive available stock (total > reserved).
            // Since available = total - reserved, we need items where total > reserved.
            // Using the repository method findByTotalQuantityGreaterThan(0) returns all
            // non-zero items, then filter in-memory for precision.
            return inventoryItemRepository.findAll().stream()
                    .filter(item -> item.getAvailableQuantity() > 0)
                    .toList();
        }
        return inventoryItemRepository.findAll();
    }

    /**
     * Check available stock for a SKU.
     *
     * @param sku              the product SKU to check
     * @param requestedQty     the number of units the caller wants (for is_available flag)
     * @return Optional containing the item (with computed available quantity), or empty if SKU not found
     */
    public Optional<InventoryItem> checkStock(String sku, int requestedQty) {
        return inventoryItemRepository.findById(sku);
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * Result of a stock reservation attempt.
     *
     * <p>This record makes the success/failure outcome explicit so the gRPC layer
     * does not need to infer it from the item state (which is ambiguous after a
     * failed reservation since the item is returned unchanged).
     *
     * @param item    the inventory item (post-reservation state if succeeded, unchanged if not)
     * @param success {@code true} if the reservation was made, {@code false} if insufficient stock
     */
    public record StockReservationResult(InventoryItem item, boolean success) {}

    /**
     * Reserve stock units for an order.
     *
     * <p>Business rules:
     * <ul>
     *   <li>If {@code quantity <= 0}, throws {@link IllegalArgumentException}.</li>
     *   <li>If the SKU is not found, returns empty Optional.</li>
     *   <li>If available quantity < requested quantity, returns a result with
     *       {@code success=false} — the gRPC layer will return {@code success=false}
     *       in the response (not a gRPC error).</li>
     *   <li>Otherwise, increments the reserved quantity and saves, returning
     *       {@code success=true}.</li>
     * </ul>
     *
     * @param sku      the product SKU
     * @param quantity the number of units to reserve
     * @return Optional containing the reservation result, or empty if SKU not found
     * @throws IllegalArgumentException if quantity is not positive
     */
    @Transactional
    public Optional<StockReservationResult> reserveStock(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive: " + quantity);
        }

        return inventoryItemRepository.findById(sku).map(item -> {
            // Check if enough stock is available before reserving.
            if (item.getAvailableQuantity() >= quantity) {
                // Sufficient stock — increment the reserved counter and persist.
                item.setReservedQuantity(item.getReservedQuantity() + quantity);
                InventoryItem saved = inventoryItemRepository.save(item);
                return new StockReservationResult(saved, true);
            }
            // Insufficient stock — return item unchanged with explicit failure flag.
            return new StockReservationResult(item, false);
        });
    }

    /**
     * Release previously reserved stock units back to available.
     *
     * <p>Business rules:
     * <ul>
     *   <li>If {@code quantity <= 0}, throws {@link IllegalArgumentException}.</li>
     *   <li>If the SKU is not found, returns empty Optional.</li>
     *   <li>The released quantity is capped at the current reserved quantity
     *       to prevent reserved from going negative.</li>
     * </ul>
     *
     * @param sku      the product SKU
     * @param quantity the number of units to release
     * @return Optional containing the updated item, or empty if SKU not found
     * @throws IllegalArgumentException if quantity is not positive
     */
    @Transactional
    public Optional<InventoryItem> releaseStock(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be positive: " + quantity);
        }

        return inventoryItemRepository.findById(sku).map(item -> {
            // Cap the release at the current reserved quantity (prevent going below 0).
            int toRelease = Math.min(quantity, item.getReservedQuantity());
            item.setReservedQuantity(item.getReservedQuantity() - toRelease);
            return inventoryItemRepository.save(item);
        });
    }
}
