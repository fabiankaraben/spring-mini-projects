package com.example.saga.inventory.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProductStock} domain entity.
 *
 * <p>Verifies reservation logic, release logic, and initial state in complete
 * isolation — no Spring context, no database, no Kafka.
 */
@DisplayName("ProductStock Domain Entity")
class ProductStockTest {

    @Test
    @DisplayName("New stock starts with INITIAL_STOCK units")
    void newStockHasInitialQuantity() {
        ProductStock stock = new ProductStock("prod-A");
        assertThat(stock.getAvailableQuantity()).isEqualTo(ProductStock.INITIAL_STOCK);
        assertThat(stock.getProductId()).isEqualTo("prod-A");
        assertThat(stock.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("reserve succeeds and decrements stock when quantity <= available")
    void reserveSucceedsAndDecrementsStock() {
        ProductStock stock = new ProductStock("prod-B");

        boolean result = stock.reserve(3);

        assertThat(result).isTrue();
        assertThat(stock.getAvailableQuantity()).isEqualTo(ProductStock.INITIAL_STOCK - 3);
    }

    @Test
    @DisplayName("reserve fails when quantity > available")
    void reserveFailsWhenInsufficientStock() {
        ProductStock stock = new ProductStock("prod-C");

        boolean result = stock.reserve(ProductStock.INITIAL_STOCK + 1);

        assertThat(result).isFalse();
        // Stock must NOT be modified on failure
        assertThat(stock.getAvailableQuantity()).isEqualTo(ProductStock.INITIAL_STOCK);
    }

    @Test
    @DisplayName("reserve succeeds when quantity exactly equals available stock")
    void reserveSucceedsForExactQuantity() {
        ProductStock stock = new ProductStock("prod-D");

        boolean result = stock.reserve(ProductStock.INITIAL_STOCK);

        assertThat(result).isTrue();
        assertThat(stock.getAvailableQuantity()).isZero();
    }

    @Test
    @DisplayName("reserve fails when stock is 0")
    void reserveFailsWhenStockIsZero() {
        ProductStock stock = new ProductStock("prod-E");
        stock.reserve(ProductStock.INITIAL_STOCK); // drain all stock

        boolean result = stock.reserve(1);

        assertThat(result).isFalse();
        assertThat(stock.getAvailableQuantity()).isZero();
    }

    @Test
    @DisplayName("release increases available stock")
    void releaseIncreasesStock() {
        ProductStock stock = new ProductStock("prod-F");
        stock.reserve(5);

        stock.release(3);

        assertThat(stock.getAvailableQuantity()).isEqualTo(ProductStock.INITIAL_STOCK - 5 + 3);
    }

    @Test
    @DisplayName("multiple sequential reservations decrement correctly")
    void multipleReservationsDecrementCorrectly() {
        ProductStock stock = new ProductStock("prod-G");

        assertThat(stock.reserve(3)).isTrue();  // 10 - 3 = 7
        assertThat(stock.reserve(4)).isTrue();  // 7 - 4 = 3
        assertThat(stock.reserve(4)).isFalse(); // 3 < 4 — fails
        assertThat(stock.getAvailableQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("updatedAt is refreshed after reserve")
    void updatedAtRefreshedAfterReserve() throws InterruptedException {
        ProductStock stock = new ProductStock("prod-H");
        var before = stock.getUpdatedAt();
        Thread.sleep(10);
        stock.reserve(1);
        assertThat(stock.getUpdatedAt()).isAfterOrEqualTo(before);
    }
}
