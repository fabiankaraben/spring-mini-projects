package com.example.serverless.service;

import com.example.serverless.domain.OrderRequest;
import com.example.serverless.domain.TaxResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Domain service that encapsulates tax rate rules and tax amount computation.
 *
 * <p>Tax rates are stored in an in-memory lookup table keyed by country code
 * (and optionally state code). In a production system this would call a tax API
 * (e.g., TaxJar, Avalara) or query a database, but here we keep it simple to
 * focus on the Spring Cloud Function patterns.
 *
 * <p>Tax rate table used in this demo:
 * <ul>
 *   <li>US/CA (California) — 8.75%</li>
 *   <li>US/NY (New York) — 8.00%</li>
 *   <li>US (default) — 7.00%</li>
 *   <li>DE (Germany / EU) — 19.00%</li>
 *   <li>GB (United Kingdom) — 20.00%</li>
 *   <li>AU (Australia / GST) — 10.00%</li>
 *   <li>Default (all others) — 5.00%</li>
 * </ul>
 */
@Service
public class TaxService {

    /**
     * Tax rates keyed by "COUNTRY/STATE" composite key.
     * A country-only entry (no slash) serves as the default for that country.
     */
    private static final Map<String, BigDecimal> TAX_RATES = Map.of(
            "US/CA", new BigDecimal("0.0875"),  // California: 8.75%
            "US/NY", new BigDecimal("0.0800"),  // New York: 8.00%
            "US",    new BigDecimal("0.0700"),  // US default: 7.00%
            "DE",    new BigDecimal("0.1900"),  // Germany VAT: 19%
            "GB",    new BigDecimal("0.2000"),  // UK VAT: 20%
            "AU",    new BigDecimal("0.1000")   // Australia GST: 10%
    );

    /**
     * Default tax rate applied when no specific rate is found for the country/state.
     * Represents a conservative 5% to avoid under-charging.
     */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.0500");

    /**
     * Calculates the tax for a given order request.
     *
     * <p>Lookup order:
     * <ol>
     *   <li>Try "COUNTRY/STATE" composite key (e.g., "US/CA").</li>
     *   <li>Fall back to country-only key (e.g., "US").</li>
     *   <li>Fall back to {@link #DEFAULT_TAX_RATE} if no match found.</li>
     * </ol>
     *
     * @param request the order for which to calculate tax
     * @return a {@link TaxResult} containing the rate, tax amount, and total
     */
    public TaxResult calculate(OrderRequest request) {
        // Build the composite country/state lookup key
        BigDecimal taxRate = resolveTaxRate(request.getCountry(), request.getState());

        // Compute tax amount: subtotal × taxRate, rounded to 2 decimal places (half-up)
        BigDecimal taxAmount = request.getSubtotal()
                .multiply(taxRate)
                .setScale(2, RoundingMode.HALF_UP);

        // Total = subtotal + tax
        BigDecimal total = request.getSubtotal().add(taxAmount);

        return new TaxResult(
                request.getOrderId(),
                request.getSubtotal(),
                taxRate,
                taxAmount,
                total
        );
    }

    /**
     * Resolves the applicable tax rate for the given country and state.
     *
     * @param country ISO 3166-1 alpha-2 country code (e.g., "US")
     * @param state   state/province code (may be null or empty)
     * @return the applicable tax rate as a decimal fraction
     */
    public BigDecimal resolveTaxRate(String country, String state) {
        // Try composite "COUNTRY/STATE" key first
        if (state != null && !state.isBlank()) {
            String compositeKey = country.toUpperCase() + "/" + state.toUpperCase();
            if (TAX_RATES.containsKey(compositeKey)) {
                return TAX_RATES.get(compositeKey);
            }
        }

        // Fall back to country-only key
        String countryKey = country.toUpperCase();
        return TAX_RATES.getOrDefault(countryKey, DEFAULT_TAX_RATE);
    }
}
