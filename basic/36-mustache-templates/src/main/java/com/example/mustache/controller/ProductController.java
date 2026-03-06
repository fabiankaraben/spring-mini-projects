package com.example.mustache.controller;

import com.example.mustache.model.Product;
import com.example.mustache.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

/**
 * MVC controller that handles HTTP GET requests and delegates rendering to
 * Mustache HTML templates ({@code .mustache} files).
 *
 * <p>
 * Key difference between {@code @Controller} and {@code @RestController}:
 * <ul>
 * <li>{@code @RestController} writes the return value directly to the HTTP
 * response body (typically as JSON).</li>
 * <li>{@code @Controller} treats the return value as a <em>view name</em>
 * that the Mustache {@code ViewResolver} resolves to a template file
 * under {@code src/main/resources/templates/} with the {@code .mustache}
 * suffix.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The {@link Model} parameter acts as a bag of key-value pairs (a
 * {@code Map<String, Object>} under the hood) that Mustache can access
 * inside templates using expressions like {@code {{products}}}.
 * In Mustache, this map is known as the <em>context</em>.
 * </p>
 *
 * <p>
 * <strong>Mustache template resolution:</strong> Spring Boot's
 * {@code MustacheViewResolver} appends ".mustache" to the logical view name.
 * So returning {@code "index"} resolves to
 * {@code classpath:/templates/index.mustache}.
 * </p>
 */
@Controller
public class ProductController {

    // Injected via constructor (preferred over field injection for testability)
    private final ProductService productService;

    /**
     * Constructor injection: Spring automatically resolves and injects the
     * {@link ProductService} bean when the controller is created.
     *
     * @param productService the service that provides product data
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // -------------------------------------------------------------------------
    // Home / Catalog page
    // -------------------------------------------------------------------------

    /**
     * Displays the product catalog.
     *
     * <p>
     * Supports an optional {@code category} query parameter. When absent, all
     * products are shown; when present, only the matching category is shown.
     * </p>
     *
     * <p>
     * URL: {@code GET /}
     * </p>
     *
     * <p>
     * <strong>Mustache note on booleans in the model:</strong>
     * Mustache sections ({{#key}}...{{/key}}) treat booleans natively.
     * We add a {@code categoryFiltered} boolean to let the template know
     * whether a category filter is active — this is the idiomatic Mustache
     * way to conditionally render UI elements.
     * </p>
     *
     * @param category optional filter (e.g. {@code ?category=Electronics})
     * @param model    Spring MVC model — attributes added here become Mustache
     *                 context variables in the template
     * @return the logical view name {@code "index"}, resolved by Mustache to
     *         {@code templates/index.mustache}
     */
    @GetMapping("/")
    public String index(
            @RequestParam(name = "category", required = false) String category,
            Model model) {

        // Choose which products to display based on the presence of the filter
        List<Product> products = (category != null && !category.isBlank())
                ? productService.findByCategory(category)
                : productService.findAll();

        // Populate the model — these keys become Mustache context variables.
        model.addAttribute("products", products);
        model.addAttribute("selectedCategory", category);

        // Mustache uses boolean sections ({{#flag}}...{{/flag}}) to conditionally
        // show/hide UI elements. We expose pre-computed booleans because Mustache
        // has no expression language — it cannot evaluate "category != null" inline.
        boolean categoryFiltered = category != null && !category.isBlank();
        model.addAttribute("categoryFiltered", categoryFiltered);

        // hasProducts is used by {{#hasProducts}} to wrap the .grid <div>.
        // Without this flag, we cannot conditionally render an outer container
        // (Mustache cannot open a div in one section and close it in another cleanly).
        model.addAttribute("hasProducts", !products.isEmpty());

        // Derive the distinct category list from all products for the filter menu.
        // Each category is wrapped in a helper record so Mustache can access
        // both the name and the "active" state with simple {{name}} syntax.
        List<CategoryItem> categories = productService.findAll().stream()
                .map(Product::category)
                .distinct()
                .sorted()
                .map(cat -> new CategoryItem(cat, cat.equals(category)))
                .toList();
        model.addAttribute("categories", categories);

        // Return the logical view name: Mustache resolves this to
        // src/main/resources/templates/index.mustache
        return "index";
    }

    // -------------------------------------------------------------------------
    // Product detail page
    // -------------------------------------------------------------------------

    /**
     * Displays the detail page for a specific product.
     *
     * <p>
     * URL: {@code GET /products/{id}}
     * </p>
     *
     * <p>
     * <strong>Mustache note on {@code inStock}:</strong>
     * The template uses {@code {{#inStock}}} and {@code {{^inStock}}} (inverted
     * section) to show/hide the "In Stock" vs "Out of Stock" badge. This works
     * because the {@code Product} record exposes {@code inStock()} which
     * Mustache resolves as a boolean.
     * </p>
     *
     * @param id    the product ID extracted from the URL path
     * @param model Spring MVC model
     * @return the view name {@code "product-detail"}, or {@code "not-found"} if
     *         the product does not exist
     */
    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model) {

        Optional<Product> product = productService.findById(id);

        if (product.isEmpty()) {
            // Resolve to not-found.mustache and set a helpful message
            model.addAttribute("message", "Product with ID " + id + " was not found.");
            return "not-found";
        }

        // Add the found product to the model so the template can render it.
        // Mustache accesses record components via their method names:
        // {{name}} → calls product.name()
        // {{category}} → calls product.category()
        // {{formattedPrice}} → calls product.formattedPrice()
        // {{#inStock}} → evaluates product.inStock() as a boolean section
        model.addAttribute("product", product.get());
        return "product-detail";
    }

    // -------------------------------------------------------------------------
    // In-stock page
    // -------------------------------------------------------------------------

    /**
     * Displays only the products that are currently in stock.
     *
     * <p>
     * URL: {@code GET /products/in-stock}
     * </p>
     *
     * <p>
     * The model also includes {@code count} — the number of available items.
     * Mustache cannot call methods like {@code .size()} inside a template, so
     * pre-computing the count on the Java side and exposing it as a model
     * attribute is the correct approach.
     * </p>
     *
     * @param model Spring MVC model
     * @return the view name {@code "in-stock"}
     */
    @GetMapping("/products/in-stock")
    public String inStock(Model model) {

        List<Product> available = productService.findInStock();
        model.addAttribute("products", available);

        // Pre-compute the size since Mustache has no expression language.
        // In the template: {{count}} items
        model.addAttribute("count", available.size());
        return "in-stock";
    }

    // -------------------------------------------------------------------------
    // Helper record (package-private, visible to tests in the same package)
    // -------------------------------------------------------------------------

    /**
     * Lightweight DTO that pairs a category name with an "active" flag.
     *
     * <p>
     * Mustache cannot evaluate expressions inside attribute values, and it has
     * no ternary operator. The idiomatic solution is to pre-compute any
     * conditional state in Java and expose it as a boolean on the model object.
     * </p>
     *
     * <p>
     * Template usage:
     * 
     * <pre>
     *   {{#categories}}
     *     &lt;a href="/?category={{name}}"
     *        class="filter-pill{{#active}} active{{/active}}"&gt;{{name}}&lt;/a&gt;
     *   {{/categories}}
     * </pre>
     * </p>
     *
     * @param name   category display name (e.g. "Electronics")
     * @param active whether this category is currently selected
     */
    record CategoryItem(String name, boolean active) {
    }
}
