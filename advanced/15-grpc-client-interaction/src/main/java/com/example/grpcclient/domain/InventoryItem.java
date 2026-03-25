package com.example.grpcclient.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity representing an inventory record for a single SKU.
 *
 * <p>The inventory tracks:
 * <ul>
 *   <li>Total physical quantity in the warehouse.</li>
 *   <li>Reserved quantity (units held for confirmed/pending orders).</li>
 *   <li>Available quantity (total - reserved) — computed in the service layer.</li>
 * </ul>
 *
 * <p>The SKU is used as the primary key, since it is already a unique identifier
 * for a product across all systems. This avoids needing a separate auto-generated
 * surrogate key for inventory records.
 *
 * <p>This entity is the persistence layer model. The corresponding protobuf
 * message ({@code InventoryItem} in inventory_service.proto) is the wire-transfer
 * representation used on the gRPC boundary.
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    /**
     * Stock Keeping Unit — the natural primary key for inventory records.
     * Example: "SKU-LAPTOP-001", "SKU-MOUSE-002".
     */
    @Id
    @Column(nullable = false, length = 100)
    private String sku;

    /**
     * Human-readable product name.
     */
    @Column(nullable = false)
    private String productName;

    /**
     * Total units physically in the warehouse (includes reserved units).
     */
    @Column(nullable = false)
    private Integer totalQuantity;

    /**
     * Units currently reserved for pending or confirmed orders.
     * Must always be <= totalQuantity.
     */
    @Column(nullable = false)
    private Integer reservedQuantity;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** JPA requires a no-arg constructor. */
    protected InventoryItem() {}

    /**
     * Full constructor used when seeding or creating new inventory records.
     *
     * @param sku               the product SKU (primary key)
     * @param productName       the product name
     * @param totalQuantity     total units in warehouse
     * @param reservedQuantity  units already reserved for orders
     */
    public InventoryItem(String sku, String productName,
                         Integer totalQuantity, Integer reservedQuantity) {
        this.sku = sku;
        this.productName = productName;
        this.totalQuantity = totalQuantity;
        this.reservedQuantity = reservedQuantity;
    }

    // -------------------------------------------------------------------------
    // Computed property
    // -------------------------------------------------------------------------

    /**
     * Returns the number of units available for new orders.
     *
     * <p>Available = total - reserved. This is a computed value, not stored
     * in the database, to avoid data inconsistency (storing derived data
     * that could get out of sync with total and reserved).
     *
     * @return units available for reservation
     */
    public Integer getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public String getSku() { return sku; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }

    public Integer getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }
}
