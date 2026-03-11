package com.example.excelexport.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Domain model representing a product that will be exported to Excel.
 *
 * <p>This is a plain Java record – immutable by design, with no JPA annotations
 * because this mini-project focuses on Excel generation rather than persistence.
 * In a real application you would typically load these from a database or external
 * service before generating the report.
 *
 * <p>Bean-validation annotations are used so that the controller can reject
 * invalid request payloads before they reach the service layer.
 */
public record Product(

        /**
         * Unique product identifier (e.g. "PRD-001").
         * Must not be blank.
         */
        @NotBlank(message = "Product id must not be blank")
        String id,

        /**
         * Human-readable product name shown in the Excel sheet.
         * Must not be blank.
         */
        @NotBlank(message = "Product name must not be blank")
        String name,

        /**
         * Category the product belongs to (e.g. "Electronics", "Books").
         * Must not be blank.
         */
        @NotBlank(message = "Category must not be blank")
        String category,

        /**
         * Unit price of the product.
         * Must not be null and must be at least 0.00.
         */
        @NotNull(message = "Price must not be null")
        @DecimalMin(value = "0.00", message = "Price must be zero or positive")
        BigDecimal price,

        /**
         * Stock quantity available.
         * Must not be null and must be zero or greater.
         */
        @NotNull(message = "Stock must not be null")
        @Min(value = 0, message = "Stock must be zero or greater")
        Integer stock
) {}
