package com.example.serverless.service;

import com.example.serverless.domain.DiscountRequest;
import com.example.serverless.domain.DiscountResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Domain service that encapsulates promotional discount code logic.
 *
 * <p>Discount codes are stored in an in-memory lookup table. In a real system
 * this would query a promotions database or call a pricing API, but here we
 * keep it simple to focus on the Spring Cloud Function patterns.
 *
 * <p>Supported discount codes:
 * <ul>
 *   <li>{@code SAVE10}   — 10% off</li>
 *   <li>{@code SAVE20}   — 20% off</li>
 *   <li>{@code HALFOFF}  — 50% off</li>
 *   <li>{@code WELCOME5} — 5% off (new customer code)</li>
 *   <li>Any unknown code — 0% (no discount, no error)</li>
 * </ul>
 */
@Service
public class DiscountService {

    /**
     * Discount percentages keyed by uppercase promotional code.
     * Values are percentages (e.g., 10 = 10%), not fractions (not 0.10).
     */
    private static final Map<String, BigDecimal> DISCOUNT_CODES = Map.of(
            "SAVE10",   new BigDecimal("10.00"),  // 10% off
            "SAVE20",   new BigDecimal("20.00"),  // 20% off
            "HALFOFF",  new BigDecimal("50.00"),  // 50% off
            "WELCOME5", new BigDecimal("5.00")    // 5% off (new customer)
    );

    /**
     * Applies a discount code to the given order total.
     *
     * <p>If the code is not recognized, a zero-discount result is returned
     * (the code field is echoed back so the caller knows what was evaluated).
     *
     * @param request the discount application request
     * @return a {@link DiscountResult} with the applied discount and final total
     */
    public DiscountResult apply(DiscountRequest request) {
        // Look up the discount percentage for the given code (case-insensitive)
        BigDecimal discountPercent = resolveDiscountPercent(request.getDiscountCode());

        // Calculate the monetary discount amount
        // Formula: originalTotal × (discountPercent / 100), rounded to 2 decimal places
        BigDecimal discountAmount = request.getOriginalTotal()
                .multiply(discountPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // Final total = originalTotal − discountAmount
        BigDecimal finalTotal = request.getOriginalTotal().subtract(discountAmount);

        return new DiscountResult(
                request.getOrderId(),
                request.getOriginalTotal(),
                request.getDiscountCode(),
                discountPercent,
                discountAmount,
                finalTotal
        );
    }

    /**
     * Looks up the discount percentage for a given code.
     *
     * @param code promotional discount code (case-insensitive)
     * @return discount percentage (0–100), or {@code BigDecimal.ZERO} if unknown
     */
    public BigDecimal resolveDiscountPercent(String code) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }
        // Normalize to uppercase for case-insensitive lookup
        return DISCOUNT_CODES.getOrDefault(code.toUpperCase(), BigDecimal.ZERO);
    }
}
