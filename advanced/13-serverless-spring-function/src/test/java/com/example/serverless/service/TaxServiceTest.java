package com.example.serverless.service;

import com.example.serverless.domain.OrderRequest;
import com.example.serverless.domain.TaxResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TaxService}.
 *
 * <p>These are pure unit tests — no Spring context, no database, no Docker.
 * They test the tax rate resolution and tax amount computation logic directly.
 *
 * <p>Testing strategy:
 * <ul>
 *   <li>Country + state lookup (composite key match)</li>
 *   <li>Country-only fallback lookup</li>
 *   <li>Unknown country/state defaults to 5%</li>
 *   <li>Zero subtotal edge case</li>
 *   <li>Correct rounding (HALF_UP, 2 decimal places)</li>
 * </ul>
 */
@DisplayName("TaxService Unit Tests")
class TaxServiceTest {

    /** The service under test. Instantiated directly — no Spring context needed. */
    private TaxService taxService;

    @BeforeEach
    void setUp() {
        // Direct instantiation: TaxService has no dependencies, so no mocking needed.
        taxService = new TaxService();
    }

    // =========================================================================
    // Tax rate resolution tests
    // =========================================================================

    @Test
    @DisplayName("California (US/CA) resolves to 8.75%")
    void taxRateForCaliforniaIs8_75Percent() {
        BigDecimal rate = taxService.resolveTaxRate("US", "CA");
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.0875"));
    }

    @Test
    @DisplayName("New York (US/NY) resolves to 8.00%")
    void taxRateForNewYorkIs8Percent() {
        BigDecimal rate = taxService.resolveTaxRate("US", "NY");
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.0800"));
    }

    @Test
    @DisplayName("US with unknown state falls back to US default (7.00%)")
    void taxRateForUsUnknownStateFallsBackToUsDefault() {
        // US/TX is not in the table; should fall back to US default
        BigDecimal rate = taxService.resolveTaxRate("US", "TX");
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.0700"));
    }

    @Test
    @DisplayName("US with no state resolves to US default (7.00%)")
    void taxRateForUsNoStateReturnsUsDefault() {
        BigDecimal rate = taxService.resolveTaxRate("US", null);
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.0700"));
    }

    @Test
    @DisplayName("Germany (DE) resolves to 19.00%")
    void taxRateForGermanyIs19Percent() {
        BigDecimal rate = taxService.resolveTaxRate("DE", null);
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.1900"));
    }

    @Test
    @DisplayName("United Kingdom (GB) resolves to 20.00%")
    void taxRateForGbIs20Percent() {
        BigDecimal rate = taxService.resolveTaxRate("GB", null);
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.2000"));
    }

    @Test
    @DisplayName("Australia (AU) resolves to 10.00%")
    void taxRateForAustraliaIs10Percent() {
        BigDecimal rate = taxService.resolveTaxRate("AU", null);
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.1000"));
    }

    @Test
    @DisplayName("Unknown country (JP) falls back to default 5.00%")
    void taxRateForUnknownCountryFallsBackToDefault() {
        BigDecimal rate = taxService.resolveTaxRate("JP", null);
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.0500"));
    }

    @Test
    @DisplayName("Lookup is case-insensitive (lowercase 'us', 'ca')")
    void taxRateLookupIsCaseInsensitive() {
        BigDecimal rate = taxService.resolveTaxRate("us", "ca");
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.0875"));
    }

    // =========================================================================
    // Tax amount calculation tests
    // =========================================================================

    @Test
    @DisplayName("California order: 199.99 × 8.75% = 17.50 tax, total 217.49")
    void calculateTaxForCaliforniaOrder() {
        // Given
        OrderRequest request = new OrderRequest(
                "ORD-001", "CUST-1", new BigDecimal("199.99"), "US", "CA");

        // When
        TaxResult result = taxService.calculate(request);

        // Then
        assertThat(result.getOrderId()).isEqualTo("ORD-001");
        assertThat(result.getSubtotal()).isEqualByComparingTo(new BigDecimal("199.99"));
        assertThat(result.getTaxRate()).isEqualByComparingTo(new BigDecimal("0.0875"));
        // 199.99 × 0.0875 = 17.499125 → rounds to 17.50 (HALF_UP)
        assertThat(result.getTaxAmount()).isEqualByComparingTo(new BigDecimal("17.50"));
        // total = 199.99 + 17.50 = 217.49
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("217.49"));
    }

    @Test
    @DisplayName("UK order: 100.00 × 20% = 20.00 tax, total 120.00")
    void calculateTaxForUkOrder() {
        // Given
        OrderRequest request = new OrderRequest(
                "ORD-002", "CUST-2", new BigDecimal("100.00"), "GB", null);

        // When
        TaxResult result = taxService.calculate(request);

        // Then
        assertThat(result.getTaxRate()).isEqualByComparingTo(new BigDecimal("0.2000"));
        assertThat(result.getTaxAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("Zero subtotal: tax amount is 0.00, total is 0.00")
    void calculateTaxForZeroSubtotal() {
        // Edge case: free order should produce zero tax
        OrderRequest request = new OrderRequest(
                "ORD-FREE", "CUST-3", BigDecimal.ZERO, "US", "CA");

        TaxResult result = taxService.calculate(request);

        assertThat(result.getTaxAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Rounding: 10.01 × 8.75% = 0.876 → rounds to 0.88 (HALF_UP)")
    void taxAmountIsRoundedHalfUp() {
        // 10.01 × 0.0875 = 0.875875 → rounds to 0.88 (HALF_UP)
        OrderRequest request = new OrderRequest(
                "ORD-003", "CUST-4", new BigDecimal("10.01"), "US", "CA");

        TaxResult result = taxService.calculate(request);

        // 0.875875 rounds to 0.88 (digit after 2nd decimal is 5, rounds up)
        assertThat(result.getTaxAmount()).isEqualByComparingTo(new BigDecimal("0.88"));
    }
}
