package com.example.serverless.service;

import com.example.serverless.domain.DiscountRequest;
import com.example.serverless.domain.DiscountResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DiscountService}.
 *
 * <p>These are pure unit tests — no Spring context, no database, no Docker.
 * They test the discount code resolution and discount amount computation logic directly.
 *
 * <p>Testing strategy:
 * <ul>
 *   <li>Known discount codes produce correct percentage and amount</li>
 *   <li>Unknown/null discount codes result in zero discount</li>
 *   <li>Case-insensitive code lookup</li>
 *   <li>Correct rounding (HALF_UP, 2 decimal places)</li>
 *   <li>Zero original total edge case</li>
 * </ul>
 */
@DisplayName("DiscountService Unit Tests")
class DiscountServiceTest {

    /** The service under test. Instantiated directly — no Spring context needed. */
    private DiscountService discountService;

    @BeforeEach
    void setUp() {
        // Direct instantiation: DiscountService has no dependencies.
        discountService = new DiscountService();
    }

    // =========================================================================
    // Discount code resolution tests
    // =========================================================================

    @Test
    @DisplayName("SAVE10 resolves to 10.00 percent")
    void save10ResolvesToTenPercent() {
        BigDecimal percent = discountService.resolveDiscountPercent("SAVE10");
        assertThat(percent).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("SAVE20 resolves to 20.00 percent")
    void save20ResolvesToTwentyPercent() {
        BigDecimal percent = discountService.resolveDiscountPercent("SAVE20");
        assertThat(percent).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("HALFOFF resolves to 50.00 percent")
    void halfOffResolvesToFiftyPercent() {
        BigDecimal percent = discountService.resolveDiscountPercent("HALFOFF");
        assertThat(percent).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("WELCOME5 resolves to 5.00 percent")
    void welcome5ResolvesToFivePercent() {
        BigDecimal percent = discountService.resolveDiscountPercent("WELCOME5");
        assertThat(percent).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("Unknown code BOGUS resolves to 0.00 percent (no error)")
    void unknownCodeResolvesToZero() {
        BigDecimal percent = discountService.resolveDiscountPercent("BOGUS");
        assertThat(percent).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Null code resolves to 0.00 percent")
    void nullCodeResolvesToZero() {
        BigDecimal percent = discountService.resolveDiscountPercent(null);
        assertThat(percent).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Blank code resolves to 0.00 percent")
    void blankCodeResolvesToZero() {
        BigDecimal percent = discountService.resolveDiscountPercent("   ");
        assertThat(percent).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Lookup is case-insensitive (lowercase 'save10')")
    void discountCodeLookupIsCaseInsensitive() {
        BigDecimal percent = discountService.resolveDiscountPercent("save10");
        assertThat(percent).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    // =========================================================================
    // Discount application tests
    // =========================================================================

    @Test
    @DisplayName("SAVE10 on 200.00: discountAmount=20.00, finalTotal=180.00")
    void applySave10OnTwoHundred() {
        // Given
        DiscountRequest request = new DiscountRequest("ORD-001", new BigDecimal("200.00"), "SAVE10");

        // When
        DiscountResult result = discountService.apply(request);

        // Then
        assertThat(result.getOrderId()).isEqualTo("ORD-001");
        assertThat(result.getOriginalTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getDiscountCode()).isEqualTo("SAVE10");
        assertThat(result.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
        // 200.00 × 10% = 20.00
        assertThat(result.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        // 200.00 - 20.00 = 180.00
        assertThat(result.getFinalTotal()).isEqualByComparingTo(new BigDecimal("180.00"));
    }

    @Test
    @DisplayName("HALFOFF on 99.99: discountAmount=50.00, finalTotal=49.99")
    void applyHalfOffOnNinetyNineNinetyNine() {
        // Given
        DiscountRequest request = new DiscountRequest("ORD-002", new BigDecimal("99.99"), "HALFOFF");

        // When
        DiscountResult result = discountService.apply(request);

        // Then
        assertThat(result.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("50.00"));
        // 99.99 × 50% = 49.995 → rounds to 50.00 (HALF_UP)
        assertThat(result.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        // 99.99 - 50.00 = 49.99
        assertThat(result.getFinalTotal()).isEqualByComparingTo(new BigDecimal("49.99"));
    }

    @Test
    @DisplayName("Unknown code results in 0% discount, finalTotal = originalTotal")
    void unknownCodeProducesZeroDiscount() {
        // Given
        DiscountRequest request = new DiscountRequest("ORD-003", new BigDecimal("150.00"), "INVALIDCODE");

        // When
        DiscountResult result = discountService.apply(request);

        // Then
        assertThat(result.getDiscountPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getFinalTotal()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("Zero original total always results in zero discount amount")
    void zeroOriginalTotalProducesZeroDiscount() {
        // Even with a valid discount code, a zero total yields zero discount
        DiscountRequest request = new DiscountRequest("ORD-FREE", BigDecimal.ZERO, "SAVE20");

        DiscountResult result = discountService.apply(request);

        assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getFinalTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
