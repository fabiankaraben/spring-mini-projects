package com.example.batchschedulers.batch.processor;

import com.example.batchschedulers.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Spring Batch {@code ItemProcessor} for the {@code priceRefreshJob}.
 *
 * <p>Simulates receiving a price update signal from an external pricing service.
 * In a real-world scenario this would call an external HTTP API or read from a
 * message queue. Here we apply a configurable percentage adjustment to each
 * product's current price to demonstrate the processing logic.
 *
 * <p>The adjustment factor is read from the application property
 * {@code batch.price-refresh.adjustment-factor} (default: {@code 1.02} = +2%).
 *
 * <p>Returning {@code null} from an {@code ItemProcessor} tells Spring Batch to
 * skip writing that item. All products are processed here (none filtered out),
 * so we never return {@code null}.
 */
@Component
public class PriceRefreshItemProcessor implements ItemProcessor<Product, Product> {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshItemProcessor.class);

    /**
     * Price adjustment factor applied to every product in each job run.
     * Defaults to 1.02 (a 2% price increase), but can be overridden in
     * application properties or environment variables.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code 1.02} → 2% increase (default)</li>
     *   <li>{@code 0.95} → 5% discount</li>
     *   <li>{@code 1.00} → no change (useful for testing)</li>
     * </ul>
     */
    @Value("${batch.price-refresh.adjustment-factor:1.02}")
    private BigDecimal adjustmentFactor;

    /**
     * Applies a price adjustment to the given product.
     *
     * <p>Delegates to {@link Product#applyPriceAdjustment(BigDecimal)} which
     * also updates the {@code lastPriceUpdate} timestamp on the entity.
     *
     * @param product the product loaded by the {@code ItemReader}
     * @return the same product with an updated price (never {@code null})
     */
    @Override
    public Product process(Product product) {
        BigDecimal oldPrice = product.getPrice();
        // Apply the adjustment factor via the domain method, which also sets lastPriceUpdate
        product.applyPriceAdjustment(adjustmentFactor);
        log.debug("PriceRefresh: product='{}' price {} -> {}",
                product.getName(), oldPrice, product.getPrice());
        return product;
    }
}
