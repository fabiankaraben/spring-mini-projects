package com.example.mustache.controller;

import com.example.mustache.model.Product;
import com.example.mustache.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced integration tests for {@link ProductController} using
 * {@code @WebMvcTest}.
 *
 * <p>
 * What {@code @WebMvcTest} does:
 * <ul>
 * <li>Starts only the <em>web layer</em> (Spring MVC, Mustache view
 * resolver, filters, etc.).</li>
 * <li>Does NOT load the full application context — no data-source, no cache,
 * etc.</li>
 * <li>Automatically configures a {@link MockMvc} instance for making fake
 * HTTP requests without starting a real HTTP server.</li>
 * </ul>
 * Because the real {@link ProductService} is not loaded, we replace it with a
 * Mockito mock using {@code @MockitoBean}.
 * </p>
 *
 * <p>
 * Why {@code @MockitoBean} instead of the deprecated {@code @MockBean}?
 * Spring Boot 3.4+ deprecated {@code @MockBean} from
 * {@code org.springframework.boot.test.mock.mockito} in favour of
 * {@code @MockitoBean} from
 * {@code org.springframework.test.context.bean.override.mockito}.
 * </p>
 *
 * <p>
 * Mustache note: {@code @WebMvcTest} also auto-configures the Mustache
 * {@code ViewResolver}, so the actual {@code .mustache} templates are rendered
 * during these tests. Assertions on {@code content().string(...)} therefore
 * verify both controller logic <em>and</em> template output.
 * </p>
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    /**
     * MockMvc is auto-configured by @WebMvcTest.
     * It lets us perform HTTP requests against the controller without running
     * an actual HTTP server.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Replaces the real ProductService bean in the (sliced) Spring context
     * with a Mockito mock. We control its return values per test.
     */
    @MockitoBean
    private ProductService productService;

    // =========================================================================
    // GET /
    // =========================================================================

    @Test
    @DisplayName("GET / returns HTTP 200 and renders the index view with all products")
    void getIndex_noFilter_returns200AndIndexView() throws Exception {
        // --- Arrange ---
        // Define the data that the mock service will return when called
        List<Product> products = List.of(
                new Product(1L, "Wireless Headphones", "Electronics", 79.99, true),
                new Product(2L, "Clean Code (Book)", "Books", 34.99, true));
        when(productService.findAll()).thenReturn(products);

        // --- Act & Assert ---
        mockMvc.perform(get("/"))
                // HTTP status must be 200 OK
                .andExpect(status().isOk())

                // The logical view name returned by the controller must be "index"
                // (Spring MVC resolves it to index.mustache via the Mustache ViewResolver)
                .andExpect(view().name("index"))

                // The model must contain the 'products' attribute
                .andExpect(model().attributeExists("products"))

                // The rendered HTML must contain both product names
                .andExpect(content().string(containsString("Wireless Headphones")))
                .andExpect(content().string(containsString("Clean Code (Book)")));

        // The controller calls findAll() twice on the unfiltered index page:
        // 1. To build the product list (when no category filter is provided)
        // 2. To derive the distinct category list for the filter menu
        verify(productService, times(2)).findAll();
    }

    @Test
    @DisplayName("GET /?category=Books filters the product list via the service")
    void getIndex_withCategoryFilter_callsFindByCategory() throws Exception {
        // --- Arrange ---
        List<Product> books = List.of(
                new Product(4L, "Clean Code (Book)", "Books", 34.99, true));
        when(productService.findByCategory("Books")).thenReturn(books);
        // findAll() is also called to build the category filter menu
        when(productService.findAll()).thenReturn(books);

        // --- Act & Assert ---
        mockMvc.perform(get("/").param("category", "Books"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                // The selected category must be present in the model
                .andExpect(model().attribute("selectedCategory", "Books"))
                // The categoryFiltered boolean must be set to true
                .andExpect(model().attribute("categoryFiltered", true))
                .andExpect(content().string(containsString("Clean Code")));

        // The service's filter method must have been called with the right argument
        verify(productService).findByCategory("Books");
    }

    @Test
    @DisplayName("GET / sets hasProducts=true when the list is non-empty")
    void getIndex_nonEmptyList_setsHasProductsTrue() throws Exception {
        // --- Arrange ---
        List<Product> products = List.of(
                new Product(1L, "Wireless Headphones", "Electronics", 79.99, true));
        when(productService.findAll()).thenReturn(products);

        // --- Act & Assert ---
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                // hasProducts controls {{#hasProducts}} in the template (renders the grid)
                .andExpect(model().attribute("hasProducts", true));
    }

    @Test
    @DisplayName("GET / sets hasProducts=false when the filtered list is empty")
    void getIndex_emptyFilteredList_setsHasProductsFalse() throws Exception {
        // --- Arrange ---
        when(productService.findByCategory("UnknownCat")).thenReturn(List.of());
        when(productService.findAll()).thenReturn(List.of());

        // --- Act & Assert ---
        mockMvc.perform(get("/").param("category", "UnknownCat"))
                .andExpect(status().isOk())
                // hasProducts=false triggers {{^hasProducts}} empty-state block in template
                .andExpect(model().attribute("hasProducts", false))
                .andExpect(content().string(containsString("No products found")));
    }

    // =========================================================================
    // GET /products/{id}
    // =========================================================================

    @Test
    @DisplayName("GET /products/{id} returns the detail view for an existing product")
    void getProductDetail_existingId_returnsDetailView() throws Exception {
        // --- Arrange ---
        Product product = new Product(1L, "Wireless Headphones", "Electronics", 79.99, true);
        when(productService.findById(1L)).thenReturn(Optional.of(product));

        // --- Act & Assert ---
        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("product-detail"))
                .andExpect(model().attributeExists("product"))
                // The rendered Mustache template must include the product name
                .andExpect(content().string(containsString("Wireless Headphones")))
                // It must also include the pre-formatted price
                .andExpect(content().string(containsString("$79.99")));
    }

    @Test
    @DisplayName("GET /products/{id} returns the not-found view for an unknown ID")
    void getProductDetail_unknownId_returnsNotFoundView() throws Exception {
        // --- Arrange ---
        when(productService.findById(999L)).thenReturn(Optional.empty());

        // --- Act & Assert ---
        mockMvc.perform(get("/products/999"))
                // HTTP 200 — the controller renders a page, not a 404 HTTP error
                .andExpect(status().isOk())
                .andExpect(view().name("not-found"))
                // The error message passed to the model must appear in the HTML
                .andExpect(content().string(containsString("999")));
    }

    // =========================================================================
    // GET /products/in-stock
    // =========================================================================

    @Test
    @DisplayName("GET /products/in-stock returns the in-stock view with available products")
    void getInStock_returnsInStockView() throws Exception {
        // --- Arrange ---
        List<Product> inStock = List.of(
                new Product(1L, "Wireless Headphones", "Electronics", 79.99, true),
                new Product(4L, "Clean Code (Book)", "Books", 34.99, true));
        when(productService.findInStock()).thenReturn(inStock);

        // --- Act & Assert ---
        mockMvc.perform(get("/products/in-stock"))
                .andExpect(status().isOk())
                .andExpect(view().name("in-stock"))
                .andExpect(model().attributeExists("products"))
                .andExpect(content().string(containsString("Wireless Headphones")));

        verify(productService, times(1)).findInStock();
    }

    @Test
    @DisplayName("GET /products/in-stock shows the correct item count in the page")
    void getInStock_templateRendersCorrectCount() throws Exception {
        // --- Arrange ---
        List<Product> inStock = List.of(
                new Product(1L, "Wireless Headphones", "Electronics", 79.99, true),
                new Product(2L, "Mechanical Keyboard", "Electronics", 129.99, true),
                new Product(4L, "Clean Code (Book)", "Books", 34.99, true));
        when(productService.findInStock()).thenReturn(inStock);

        // --- Act & Assert ---
        mockMvc.perform(get("/products/in-stock"))
                .andExpect(status().isOk())
                // The controller sets model.addAttribute("count", 3)
                // Mustache renders {{count}} → "3" inside the count badge
                .andExpect(model().attribute("count", 3))
                .andExpect(content().string(containsString("3 items")));
    }
}
