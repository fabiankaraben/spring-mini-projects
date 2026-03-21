package com.example.tracing.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Order} domain record.
 *
 * <p>Tests focus on Bean Validation constraints — ensuring that invalid input
 * is rejected before it reaches the service layer. These are pure unit tests
 * with no Spring context, so they are extremely fast.
 *
 * <p><b>Why test validation here?</b>
 * In a controller test ({@code @WebMvcTest}), Spring MVC handles validation
 * automatically. But testing the validation annotations directly on the record
 * verifies that the constraints are correctly declared independently of the
 * web layer — important for reuse in other contexts (e.g., message-driven consumers).
 */
@DisplayName("Order — domain validation tests")
class OrderTest {

    /** Hibernate Validator instance — used directly without Spring context. */
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        // Bootstrap a standalone validator (no Spring needed).
        // ValidatorFactory is heavyweight so it is shared across all test methods.
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // =========================================================================
    // Valid order
    // =========================================================================

    /**
     * A fully valid order should produce zero constraint violations.
     */
    @Test
    @DisplayName("Valid order produces no constraint violations")
    void validOrderShouldProduceNoViolations() {
        Order order = new Order("ORD-001", "PROD-001", 2, "Alice");
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations).isEmpty();
    }

    // =========================================================================
    // orderId validation
    // =========================================================================

    /**
     * A blank orderId violates the @NotBlank constraint.
     */
    @Test
    @DisplayName("Blank orderId violates @NotBlank")
    void blankOrderIdShouldViolateNotBlank() {
        Order order = new Order("", "PROD-001", 1, "Alice");
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("orderId"));
    }

    /**
     * A null orderId violates the @NotBlank constraint.
     */
    @Test
    @DisplayName("Null orderId violates @NotBlank")
    void nullOrderIdShouldViolateNotBlank() {
        Order order = new Order(null, "PROD-001", 1, "Alice");
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("orderId"));
    }

    // =========================================================================
    // productId validation
    // =========================================================================

    /**
     * A blank productId violates the @NotBlank constraint.
     */
    @Test
    @DisplayName("Blank productId violates @NotBlank")
    void blankProductIdShouldViolateNotBlank() {
        Order order = new Order("ORD-001", "  ", 1, "Alice");
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("productId"));
    }

    // =========================================================================
    // quantity validation
    // =========================================================================

    /**
     * A null quantity violates the @NotNull constraint.
     */
    @Test
    @DisplayName("Null quantity violates @NotNull")
    void nullQuantityShouldViolateNotNull() {
        Order order = new Order("ORD-001", "PROD-001", null, "Alice");
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    /**
     * A quantity of zero violates the @Min(1) constraint.
     */
    @Test
    @DisplayName("Quantity zero violates @Min(1)")
    void zeroQuantityShouldViolateMin() {
        Order order = new Order("ORD-001", "PROD-001", 0, "Alice");
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    /**
     * A negative quantity violates the @Min(1) constraint.
     */
    @Test
    @DisplayName("Negative quantity violates @Min(1)")
    void negativeQuantityShouldViolateMin() {
        Order order = new Order("ORD-001", "PROD-001", -5, "Alice");
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    /**
     * A quantity of exactly 1 is valid.
     */
    @Test
    @DisplayName("Quantity of 1 is valid (minimum boundary)")
    void quantityOfOneShouldBeValid() {
        Order order = new Order("ORD-001", "PROD-001", 1, "Alice");
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations).isEmpty();
    }

    // =========================================================================
    // customer validation
    // =========================================================================

    /**
     * A blank customer violates the @NotBlank constraint.
     */
    @Test
    @DisplayName("Blank customer violates @NotBlank")
    void blankCustomerShouldViolateNotBlank() {
        Order order = new Order("ORD-001", "PROD-001", 1, "");
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("customer"));
    }

    // =========================================================================
    // Record accessor tests
    // =========================================================================

    /**
     * Java record accessors return the correct component values.
     */
    @Test
    @DisplayName("Record accessors return the correct values")
    void recordAccessorsShouldReturnCorrectValues() {
        Order order = new Order("ORD-007", "PROD-003", 3, "Bob");
        assertThat(order.orderId()).isEqualTo("ORD-007");
        assertThat(order.productId()).isEqualTo("PROD-003");
        assertThat(order.quantity()).isEqualTo(3);
        assertThat(order.customer()).isEqualTo("Bob");
    }
}
