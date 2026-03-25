package com.example.grpcclient.repository;

import com.example.grpcclient.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link InventoryItem} entities.
 *
 * <p>The primary key type is {@code String} — the SKU is the natural key for inventory.
 */
@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {

    /**
     * Find all inventory items where available quantity (total - reserved) is greater than zero.
     *
     * <p>Spring Data translates this method into a JPQL query using the two stored columns:
     * <pre>
     * SELECT i FROM InventoryItem i
     * WHERE (i.totalQuantity - i.reservedQuantity) > 0
     * </pre>
     *
     * <p>This is used by the ListInventory RPC when {@code onlyAvailable=true}.
     *
     * @return list of items with at least one unit available; may be empty
     */
    List<InventoryItem> findByTotalQuantityGreaterThan(int minTotal);
}
