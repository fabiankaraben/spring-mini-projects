package com.example.entitylifecycle.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the domain logic embedded in {@link Product} via JPA lifecycle
 * callback annotations.
 *
 * <p><b>Testing approach:</b>
 * <ul>
 *   <li>No Spring context is loaded — tests run as plain JUnit 5 tests, which
 *       makes them extremely fast.</li>
 *   <li>Because the JPA lifecycle callbacks ({@code @PrePersist},
 *       {@code @PostLoad}) are plain Java methods on the entity, they can be
 *       called directly using reflection or by simulating what Hibernate does
 *       (calling the annotated method directly).</li>
 *   <li>We invoke the private/package-private callback methods via reflection so
 *       we can test them in isolation without a running EntityManager.</li>
 * </ul>
 *
 * <p><b>What these tests cover:</b>
 * <ul>
 *   <li>Slug generation logic in {@code @PrePersist} — various name formats.</li>
 *   <li>Timestamp initialisation in {@code @PrePersist}.</li>
 *   <li>Discount price calculation in {@code @PostLoad} / {@code @PrePersist}.</li>
 * </ul>
 */
class ProductLifecycleTest {

    // =========================================================================
    // Helper — invoke the @PrePersist callback directly
    // =========================================================================

    /**
     * Simulates the JPA container calling {@code @PrePersist} on the given
     * product by invoking the package-private {@code onPrePersist()} method via
     * reflection.
     *
     * <p>In production Hibernate calls this automatically before the INSERT; here
     * we call it manually so we can unit-test the business logic without a
     * database or Spring context.
     */
    private void triggerPrePersist(Product product) throws Exception {
        var method = Product.class.getDeclaredMethod("onPrePersist");
        method.setAccessible(true);
        method.invoke(product);
    }

    /**
     * Simulates the JPA container calling {@code @PostLoad} on the given product
     * by invoking the package-private {@code onPostLoad()} method via reflection.
     */
    private void triggerPostLoad(Product product) throws Exception {
        var method = Product.class.getDeclaredMethod("onPostLoad");
        method.setAccessible(true);
        method.invoke(product);
    }

    // =========================================================================
    // @PrePersist — slug generation
    // =========================================================================

    @Test
    @DisplayName("@PrePersist generates a lowercase hyphenated slug from a simple name")
    void prePersist_generatesSlug_fromSimpleName() throws Exception {
        // given — a product with a simple two-word name
        Product product = new Product("Wireless Headphones", "Great sound", new BigDecimal("99.99"), 0);

        // when — simulate the JPA container calling @PrePersist
        triggerPrePersist(product);

        // then — slug should be lowercase and hyphenated
        assertThat(product.getSlug()).isEqualTo("wireless-headphones");
    }

    @Test
    @DisplayName("@PrePersist generates a slug stripping special characters")
    void prePersist_generatesSlug_strippingSpecialChars() throws Exception {
        // given — a name with parentheses, slashes, and other non-alphanumeric chars
        Product product = new Product("Headphones (v2.0) — Pro!", "Description", new BigDecimal("49.99"), 0);

        // when
        triggerPrePersist(product);

        // then — all non-alphanumeric sequences are replaced by a single hyphen
        assertThat(product.getSlug()).isEqualTo("headphones-v2-0-pro");
    }

    @Test
    @DisplayName("@PrePersist generates a slug with no leading or trailing hyphens")
    void prePersist_generatesSlug_noLeadingOrTrailingHyphens() throws Exception {
        // given — name that starts and ends with non-alphanumeric chars
        Product product = new Product("  Best Keyboard  ", "Mechanical", new BigDecimal("150.00"), 0);

        // when
        triggerPrePersist(product);

        // then — leading/trailing hyphens (from the surrounding spaces) are stripped
        assertThat(product.getSlug()).isEqualTo("best-keyboard");
    }

    @Test
    @DisplayName("@PrePersist collapses consecutive spaces/hyphens into a single hyphen")
    void prePersist_generatesSlug_collapsingConsecutiveHyphens() throws Exception {
        // given — name with multiple consecutive spaces
        Product product = new Product("USB   Type-C  Cable", "Fast charging", new BigDecimal("19.99"), 0);

        // when
        triggerPrePersist(product);

        // then — consecutive spaces/hyphens are collapsed into one
        assertThat(product.getSlug()).isEqualTo("usb-type-c-cable");
    }

    @Test
    @DisplayName("@PrePersist produces different slugs for names that differ only in case")
    void prePersist_generatesSlug_caseDifferentiation() throws Exception {
        // given
        Product p1 = new Product("Gaming Mouse", "For gaming", new BigDecimal("59.99"), 0);
        Product p2 = new Product("GAMING MOUSE", "For gaming", new BigDecimal("59.99"), 0);

        // when
        triggerPrePersist(p1);
        triggerPrePersist(p2);

        // then — both map to the same slug (database unique constraint handles duplicates)
        assertThat(p1.getSlug()).isEqualTo("gaming-mouse");
        assertThat(p2.getSlug()).isEqualTo("gaming-mouse");
    }

    // =========================================================================
    // @PrePersist — timestamp initialisation
    // =========================================================================

    @Test
    @DisplayName("@PrePersist sets createdAt and updatedAt to a non-null instant")
    void prePersist_setsTimestamps() throws Exception {
        // given
        Product product = new Product("Monitor", "4K display", new BigDecimal("399.99"), 0);

        // when
        triggerPrePersist(product);

        // then — both timestamps must be non-null after @PrePersist
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("@PrePersist sets createdAt equal to updatedAt on initial INSERT")
    void prePersist_createdAtEqualsUpdatedAt_onInsert() throws Exception {
        // given
        Product product = new Product("Webcam", "HD", new BigDecimal("79.99"), 0);

        // when
        triggerPrePersist(product);

        // then — on first save both timestamps must be the same instant
        assertThat(product.getCreatedAt()).isEqualTo(product.getUpdatedAt());
    }

    // =========================================================================
    // @PrePersist / @PostLoad — discounted price calculation
    // =========================================================================

    @Test
    @DisplayName("@PrePersist computes discountedPrice correctly for a non-zero discount")
    void prePersist_computesDiscountedPrice_withDiscount() throws Exception {
        // given — $100.00 with a 20% discount → $80.00
        Product product = new Product("Tablet", "Great display", new BigDecimal("100.00"), 20);

        // when
        triggerPrePersist(product);

        // then
        assertThat(product.getDiscountedPrice()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("@PrePersist computes discountedPrice equal to price when discount is 0")
    void prePersist_computesDiscountedPrice_zeroDiscount() throws Exception {
        // given — $50.00 with no discount → $50.00
        Product product = new Product("Keyboard", "Mechanical", new BigDecimal("50.00"), 0);

        // when
        triggerPrePersist(product);

        // then
        assertThat(product.getDiscountedPrice()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("@PrePersist computes discountedPrice as 0.00 when discount is 100%")
    void prePersist_computesDiscountedPrice_fullDiscount() throws Exception {
        // given — $200.00 with 100% discount → $0.00
        Product product = new Product("Promo Item", "Free!", new BigDecimal("200.00"), 100);

        // when
        triggerPrePersist(product);

        // then
        assertThat(product.getDiscountedPrice()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("@PrePersist rounds discountedPrice to 2 decimal places using HALF_UP")
    void prePersist_computesDiscountedPrice_roundedToTwoDecimals() throws Exception {
        // given — $9.99 with 10% discount: 9.99 * 0.90 = 8.991 → rounds to 8.99
        Product product = new Product("Cheap Item", "Budget", new BigDecimal("9.99"), 10);

        // when
        triggerPrePersist(product);

        // then — result should be rounded to exactly 2 decimal places
        assertThat(product.getDiscountedPrice()).isEqualByComparingTo("8.99");
    }

    @Test
    @DisplayName("@PostLoad recomputes discountedPrice after the discount is changed")
    void postLoad_recomputesDiscountedPrice_afterDiscountChange() throws Exception {
        // given — create a product and simulate @PrePersist at 0% discount
        Product product = new Product("Laptop", "Powerful", new BigDecimal("1000.00"), 0);
        triggerPrePersist(product);

        // Verify initial discounted price equals full price (no discount)
        assertThat(product.getDiscountedPrice()).isEqualByComparingTo("1000.00");

        // Simulate the scenario: discountPercent is updated in the DB to 15%
        // and the entity is re-loaded. We replicate that by setting the discount
        // and then triggering @PostLoad manually.
        product.setDiscountPercent(15);

        // when — simulate Hibernate calling @PostLoad after re-reading from DB
        triggerPostLoad(product);

        // then — discountedPrice must reflect the updated 15% discount
        assertThat(product.getDiscountedPrice()).isEqualByComparingTo("850.00");
    }

    @Test
    @DisplayName("@PostLoad sets discountedPrice to null when price is null")
    void postLoad_setsDiscountedPriceToNull_whenPriceIsNull() throws Exception {
        // given — product with null price (edge case)
        Product product = new Product("No Price", "Unknown", null, 10);

        // when
        triggerPostLoad(product);

        // then — graceful null handling
        assertThat(product.getDiscountedPrice()).isNull();
    }
}
