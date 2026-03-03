package com.example.queryparser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// @Validated must be applied to the controller class to enable method-level validation
// for individual @RequestParam arguments.
@RestController
@RequestMapping("/api/products")
@Validated
public class ProductSearchController {

    // Educational Note: We can use a Java Record to map multiple query parameters
    // into a single object.
    // The @Valid annotation triggers validation based on the constraints inside the
    // record.
    public record ProductSearchCriteria(
            @NotBlank(message = "Query cannot be blank") @Size(min = 3, max = 50, message = "Query must be between 3 and 50 characters") String q,

            @Min(value = 0, message = "Minimum price cannot be negative") Integer minPrice,

            @Min(value = 0, message = "Maximum price cannot be negative") Integer maxPrice) {
    }

    /**
     * Endpoint resolving a POJO from Query Parameters.
     * Use @Valid to validate the ProductSearchCriteria fields.
     */
    @GetMapping("/search-pojo")
    public Map<String, Object> searchWithPojo(@Valid ProductSearchCriteria criteria) {
        // Echo back the parsed and validated criteria
        return Map.of(
                "query", criteria.q(),
                "minPrice", criteria.minPrice() != null ? criteria.minPrice() : "Not specified",
                "maxPrice", criteria.maxPrice() != null ? criteria.maxPrice() : "Not specified",
                "message", "Search executed successfully via POJO mapping");
    }

    /**
     * Endpoint resolving individual Query Parameters.
     * 
     * @RequestParam extracts the query variables. Since the class has @Validated,
     *               the @Constraint annotations like @NotBlank and @Min will be
     *               evaluated.
     */
    @GetMapping("/search-params")
    public Map<String, Object> searchWithParams(
            @RequestParam(name = "category") @NotBlank(message = "Category is required") String category,
            @RequestParam(name = "limit", defaultValue = "10") @Min(value = 1, message = "Limit must be at least 1") @Max(value = 100, message = "Limit cannot exceed 100") int limit,
            @RequestParam(name = "tags", required = false) List<String> tags) {
        // Echo back the parameters
        return Map.of(
                "category", category,
                "limit", limit,
                "tags", tags != null ? tags : List.of(),
                "message", "Search executed successfully via individual @RequestParam");
    }
}
