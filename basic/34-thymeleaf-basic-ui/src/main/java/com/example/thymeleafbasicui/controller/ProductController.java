package com.example.thymeleafbasicui.controller;

import com.example.thymeleafbasicui.model.Product;
import com.example.thymeleafbasicui.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

/**
 * MVC controller that handles HTTP requests and delegates rendering to
 * Thymeleaf HTML templates.
 *
 * <p>
 * Key difference between {@code @Controller} and {@code @RestController}:
 * <ul>
 * <li>{@code @RestController} writes the return value directly to the HTTP
 * response body (typically as JSON).</li>
 * <li>{@code @Controller} treats the return value as a <em>view name</em>
 * that Thymeleaf resolves to an HTML file under
 * {@code src/main/resources/templates/}.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The {@link Model} parameter acts as a bag of key-value pairs that Thymeleaf
 * can read inside the template using expressions like {@code ${products}}.
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
     * @param category optional filter (e.g. {@code ?category=Electronics})
     * @param model    Spring MVC model — attributes added here are available in the
     *                 template
     * @return the logical view name {@code "index"}, resolved to
     *         {@code templates/index.html} by Thymeleaf
     */
    @GetMapping("/")
    public String index(
            @RequestParam(name = "category", required = false) String category,
            Model model) {

        // Choose which products to display based on the presence of the filter
        List<Product> products = (category != null && !category.isBlank())
                ? productService.findByCategory(category)
                : productService.findAll();

        // Populate the model — these keys become Thymeleaf variables
        model.addAttribute("products", products);
        model.addAttribute("selectedCategory", category);

        // Derive the distinct category list from all products for the filter menu
        List<String> categories = productService.findAll().stream()
                .map(Product::category)
                .distinct()
                .sorted()
                .toList();
        model.addAttribute("categories", categories);

        // Return the logical view name: Thymeleaf resolves this to
        // src/main/resources/templates/index.html
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
     * @param id    the product ID extracted from the URL path
     * @param model Spring MVC model
     * @return the view name {@code "product-detail"}, or {@code "not-found"} if
     *         the product does not exist
     */
    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model) {

        Optional<Product> product = productService.findById(id);

        if (product.isEmpty()) {
            // Resolve to not-found.html and set a helpful message
            model.addAttribute("message", "Product with ID " + id + " was not found.");
            return "not-found";
        }

        // Add the found product to the model so the template can render it
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
     * @param model Spring MVC model
     * @return the view name {@code "in-stock"}
     */
    @GetMapping("/products/in-stock")
    public String inStock(Model model) {

        List<Product> available = productService.findInStock();
        model.addAttribute("products", available);
        return "in-stock";
    }
}
