package com.example.mustache.model;

/**
 * Represents a single item in the product catalog.
 *
 * <p>
 * This is a plain Java record used as a view model (DTO) passed from the
 * service layer to the Mustache templates. Records are immutable by design,
 * which is ideal here because the template should only <em>read</em> the data,
 * never mutate it.
 * </p>
 *
 * <p>
 * <strong>How Mustache accesses Java record components:</strong><br>
 * Mustache (via JMustache, the library used by Spring Boot) resolves
 * {@code {{name}}} by calling {@code getName()} on the model object first,
 * and falls back to calling the no-arg method {@code name()} if no JavaBean
 * getter is found. Both styles work with this record.
 *
 * Example:
 * 
 * <pre>
 *   {{name}}     → calls product.name()     (record component accessor)
 *   {{category}} → calls product.category() (record component accessor)
 *   {{inStock}}  → calls product.inStock()  (record component accessor)
 * </pre>
 *
 * Unlike FreeMarker, Mustache does NOT require explicit parentheses in the
 * template — {@code {{name}}} just works without needing {@code {{name()}}}.
 * </p>
 *
 * @param id       unique identifier
 * @param name     display name of the product
 * @param category category it belongs to (e.g. "Electronics", "Books")
 * @param price    price in USD
 * @param inStock  whether the product is currently available for purchase
 */
public record Product(
        Long id,
        String name,
        String category,
        double price,
        boolean inStock) {

    /**
     * Returns the price formatted as a USD string with two decimal places.
     *
     * <p>
     * Mustache does not have built-in number-formatting helpers the way
     * FreeMarker does ({@code ?string("0.00")} in FTL). The idiomatic Mustache
     * approach is to pre-format the value on the Java side and expose it as a
     * plain string property on the model object.
     *
     * <p>
     * Usage in template: {@code {{formattedPrice}}} → e.g. {@code "$79.99"}
     *
     * @return formatted price string, e.g. {@code "$79.99"}
     */
    public String formattedPrice() {
        return String.format("$%.2f", price);
    }
}
