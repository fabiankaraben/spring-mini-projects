package com.example.excelexport.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request payload sent by the client when asking for a custom Excel report.
 *
 * <p>The client provides a title for the sheet and a list of products to include.
 * Bean-validation cascades down to each {@link Product} via {@code @Valid}.
 *
 * <p>Example JSON body:
 * <pre>{@code
 * {
 *   "reportTitle": "Q1 Inventory",
 *   "products": [
 *     { "id": "PRD-001", "name": "Laptop", "category": "Electronics",
 *       "price": 999.99, "stock": 42 }
 *   ]
 * }
 * }</pre>
 */
public record ExcelReportRequest(

        /**
         * Title rendered in the first row of the Excel sheet.
         * Must not be blank.
         */
        @NotBlank(message = "Report title must not be blank")
        String reportTitle,

        /**
         * Products to include in the report.
         * Must contain at least one entry; each entry is fully validated.
         */
        @NotEmpty(message = "Products list must not be empty")
        @Valid
        List<Product> products
) {}
