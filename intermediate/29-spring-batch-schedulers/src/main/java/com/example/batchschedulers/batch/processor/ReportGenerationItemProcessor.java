package com.example.batchschedulers.batch.processor;

import com.example.batchschedulers.model.Product;
import com.example.batchschedulers.model.ProductReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Spring Batch {@code ItemProcessor} for the {@code reportGenerationJob}.
 *
 * <p>Unlike the other two jobs that produce one output item per input item,
 * this processor accumulates all products into an in-memory map keyed by
 * category. Because Spring Batch runs the processor once per item, the
 * single-product-to-single-report mapping does not apply cleanly here.
 *
 * <p>Design choice: This processor generates a {@code ProductReport} for the
 * <em>category</em> of the current product by aggregating all products whose
 * category has <strong>already been accumulated</strong> up to this point. Only
 * the first product seen in each category produces a non-null output (triggering
 * the writer). Subsequent products in the same category return {@code null}
 * (filtered). The first product of each category carries the full category
 * statistics, which are computed up front from all products loaded in memory by
 * the {@code ItemReader}.
 *
 * <p>A simpler alternative design would use a custom {@code ItemWriter} or
 * {@code Tasklet} to compute all aggregates in one shot. The approach here is
 * intentionally chunk-oriented to illustrate how an {@code ItemProcessor} can
 * work with aggregation while remaining compatible with Spring Batch's chunk model.
 *
 * <p><strong>Important:</strong> The pre-computed statistics are injected by
 * the {@code ReportGenerationJobConfig} via {@link #setCategoryStats(Map)} before
 * the step starts.
 */
@Component
public class ReportGenerationItemProcessor implements ItemProcessor<Product, ProductReport> {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationItemProcessor.class);

    /**
     * Pre-computed per-category statistics map.
     * Key: category name. Value: aggregated stats for all products in that category.
     * Set by the job configuration before the step runs.
     */
    private Map<String, CategoryStats> categoryStats = new ConcurrentHashMap<>();

    /**
     * Tracks which categories have already emitted a report row in this run.
     * Cleared at the start of each job execution by the job configuration.
     */
    private final Map<String, Boolean> emittedCategories = new ConcurrentHashMap<>();

    /**
     * Processes one product: generates a {@code ProductReport} for its category
     * on the first occurrence, returns {@code null} for subsequent products in the
     * same category (Spring Batch will filter these out).
     *
     * @param product the current product item from the reader
     * @return a {@code ProductReport} for the product's category (first occurrence only),
     *         or {@code null} to skip (subsequent products in the same category)
     */
    @Override
    public ProductReport process(Product product) {
        String category = product.getCategory();

        // Only emit one report row per category per job run
        if (emittedCategories.containsKey(category)) {
            // Already emitted a report for this category → filter this item out
            return null;
        }

        // Mark this category as emitted so subsequent products are filtered
        emittedCategories.put(category, Boolean.TRUE);

        // Look up pre-computed stats for this category
        CategoryStats stats = categoryStats.get(category);
        if (stats == null) {
            log.warn("ReportGeneration: no stats found for category '{}', skipping", category);
            return null;
        }

        ProductReport report = new ProductReport(
                category,
                stats.productCount(),
                stats.totalStock(),
                stats.averagePrice(),
                stats.lowStockCount()
        );
        log.debug("ReportGeneration: generated report for category='{}' count={} avgPrice={}",
                category, stats.productCount(), stats.averagePrice());
        return report;
    }

    /**
     * Sets the pre-computed category statistics. Called by the job configuration
     * before the step starts so the processor has access to aggregate data.
     *
     * @param allProducts the full list of products to aggregate by category
     */
    public void initializeCategoryStats(List<Product> allProducts) {
        // Clear state from any previous run
        emittedCategories.clear();
        categoryStats.clear();

        // Group products by category and compute per-category statistics
        Map<String, List<Product>> byCategory = allProducts.stream()
                .collect(Collectors.groupingBy(Product::getCategory));

        byCategory.forEach((category, products) -> {
            int count = products.size();
            int totalStock = products.stream().mapToInt(Product::getStockQuantity).sum();
            int lowStockCount = (int) products.stream().filter(Product::isLowStock).count();

            // Compute average price, rounded to 2 decimal places
            BigDecimal totalPrice = products.stream()
                    .map(Product::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgPrice = (count > 0)
                    ? totalPrice.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            categoryStats.put(category, new CategoryStats(count, totalStock, avgPrice, lowStockCount));
        });

        log.debug("ReportGeneration: initialized stats for {} categories", categoryStats.size());
    }

    /**
     * Immutable value object holding aggregated statistics for one product category.
     * Uses a Java 16+ record for concise definition.
     *
     * @param productCount  total number of products in the category
     * @param totalStock    sum of stock quantities
     * @param averagePrice  mean unit price (rounded to 2 dp)
     * @param lowStockCount number of low-stock products
     */
    public record CategoryStats(int productCount, int totalStock, BigDecimal averagePrice, int lowStockCount) {}
}
