package com.example.batchschedulers.batch.processor;

import com.example.batchschedulers.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Spring Batch {@code ItemProcessor} for the {@code inventoryAuditJob}.
 *
 * <p>Inspects each product's current stock level and delegates to
 * {@link Product#auditInventory()} which sets or clears the {@code lowStock}
 * flag and updates the {@code lastAudited} timestamp.
 *
 * <p>Products with stock below {@link Product#LOW_STOCK_THRESHOLD} ({@value Product#LOW_STOCK_THRESHOLD} units)
 * are flagged as low-stock. The actual flagging logic lives in the domain model
 * so that it can be unit-tested independently of the batch infrastructure.
 *
 * <p>All products pass through this processor — returning {@code null} is never
 * done here because we always want to update the audit timestamp even for
 * well-stocked products.
 */
@Component
public class InventoryAuditItemProcessor implements ItemProcessor<Product, Product> {

    private static final Logger log = LoggerFactory.getLogger(InventoryAuditItemProcessor.class);

    /**
     * Audits the inventory level of the given product.
     *
     * <p>Calls {@link Product#auditInventory()} which:
     * <ol>
     *   <li>Sets {@code lowStock = true} if {@code stockQuantity < LOW_STOCK_THRESHOLD}</li>
     *   <li>Clears {@code lowStock} otherwise</li>
     *   <li>Sets {@code lastAudited} to {@code LocalDateTime.now()}</li>
     * </ol>
     *
     * @param product the product loaded by the {@code ItemReader}
     * @return the same product with updated audit fields (never {@code null})
     */
    @Override
    public Product process(Product product) {
        boolean wasLowStock = product.isLowStock();
        // Domain method: applies the low-stock threshold check and sets lastAudited
        product.auditInventory();
        if (product.isLowStock() && !wasLowStock) {
            log.warn("InventoryAudit: product='{}' now LOW STOCK (qty={})",
                    product.getName(), product.getStockQuantity());
        } else if (!product.isLowStock() && wasLowStock) {
            log.info("InventoryAudit: product='{}' stock restored (qty={})",
                    product.getName(), product.getStockQuantity());
        } else {
            log.debug("InventoryAudit: product='{}' qty={} lowStock={}",
                    product.getName(), product.getStockQuantity(), product.isLowStock());
        }
        return product;
    }
}
