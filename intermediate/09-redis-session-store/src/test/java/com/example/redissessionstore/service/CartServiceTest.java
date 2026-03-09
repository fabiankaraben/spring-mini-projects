package com.example.redissessionstore.service;

import com.example.redissessionstore.domain.CartItem;
import com.example.redissessionstore.dto.CartItemRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CartService}.
 *
 * <p>These tests exercise the service's business logic in pure isolation:
 * <ul>
 *   <li>{@link HttpSession} is replaced with a Mockito mock, so no servlet
 *       container or Redis connection is required.</li>
 *   <li>No Spring context is loaded — tests run in milliseconds.</li>
 *   <li>Spring Session's transparent Redis persistence is NOT active here (it
 *       requires the full Spring container). Integration tests cover that behaviour
 *       end-to-end via Testcontainers.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartService unit tests")
class CartServiceTest {

    /** Mocked HTTP session — no servlet container, no Redis. */
    @Mock
    private HttpSession session;

    /**
     * The class under test.
     * {@code @InjectMocks} creates an instance and injects the {@code @Mock} fields.
     */
    @InjectMocks
    private CartService cartService;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    private CartItemRequest laptopRequest;
    private CartItemRequest mouseRequest;

    @BeforeEach
    void setUp() {
        // Reusable request fixtures
        laptopRequest = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 1);
        mouseRequest  = new CartItemRequest("p2", "Mouse",  new BigDecimal("29.99"),  2);
    }

    // ── getCart ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCart returns empty list when session has no cart attribute")
    void getCart_returnsEmptyList_whenNoCartInSession() {
        // Given: session has no cart attribute (first visit)
        when(session.getAttribute(CartService.CART_SESSION_KEY)).thenReturn(null);

        // When
        List<CartItem> cart = cartService.getCart(session);

        // Then: an empty list is returned (not null)
        assertThat(cart).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("getCart returns existing cart items from session")
    void getCart_returnsExistingItems_whenCartInSession() {
        // Given: session already has a cart with one item
        List<CartItem> existing = new ArrayList<>();
        existing.add(new CartItem("p1", "Laptop", new BigDecimal("999.99"), 1));
        when(session.getAttribute(CartService.CART_SESSION_KEY)).thenReturn(existing);

        // When
        List<CartItem> cart = cartService.getCart(session);

        // Then: the same list is returned
        assertThat(cart).hasSize(1);
        assertThat(cart.get(0).getProductId()).isEqualTo("p1");
    }

    // ── getCartTotal ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCartTotal returns zero for an empty cart")
    void getCartTotal_returnsZero_whenCartEmpty() {
        // Given: session has no cart attribute
        when(session.getAttribute(CartService.CART_SESSION_KEY)).thenReturn(null);

        // When
        BigDecimal total = cartService.getCartTotal(session);

        // Then: total is zero
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getCartTotal sums all line totals correctly")
    void getCartTotal_sumsLineTotals() {
        // Given: a cart with two items
        // Item 1: 999.99 * 1 = 999.99
        // Item 2:  29.99 * 2 =  59.98
        // Total              = 1059.97
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("p1", "Laptop", new BigDecimal("999.99"), 1));
        items.add(new CartItem("p2", "Mouse",  new BigDecimal("29.99"),  2));
        when(session.getAttribute(CartService.CART_SESSION_KEY)).thenReturn(items);

        // When
        BigDecimal total = cartService.getCartTotal(session);

        // Then
        assertThat(total).isEqualByComparingTo("1059.97");
    }

    // ── addItem ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addItem adds a new product to an empty cart")
    void addItem_addsNewProduct_toEmptyCart() {
        // Given: session starts with no cart attribute
        when(session.getAttribute(CartService.CART_SESSION_KEY)).thenReturn(null);

        // When
        List<CartItem> cart = cartService.addItem(laptopRequest, session);

        // Then: the cart now contains one item
        assertThat(cart).hasSize(1);
        assertThat(cart.get(0).getProductId()).isEqualTo("p1");
        assertThat(cart.get(0).getProductName()).isEqualTo("Laptop");
        assertThat(cart.get(0).getQuantity()).isEqualTo(1);

        // And: setAttribute was called twice — once to lazily initialise the
        // empty cart in the session, and once to save the cart after adding the item
        verify(session, org.mockito.Mockito.times(2)).setAttribute(eq(CartService.CART_SESSION_KEY), any());
    }

    @Test
    @DisplayName("addItem increments quantity when same product is added again")
    void addItem_incrementsQuantity_whenProductAlreadyInCart() {
        // Given: the cart already has 1 laptop
        List<CartItem> existingCart = new ArrayList<>();
        existingCart.add(new CartItem("p1", "Laptop", new BigDecimal("999.99"), 1));
        when(session.getAttribute(CartService.CART_SESSION_KEY)).thenReturn(existingCart);

        // When: the same product is added again with quantity 2
        CartItemRequest addMoreRequest = new CartItemRequest("p1", "Laptop", new BigDecimal("999.99"), 2);
        List<CartItem> cart = cartService.addItem(addMoreRequest, session);

        // Then: there is still only ONE entry (no duplicate), but quantity is now 3
        assertThat(cart).hasSize(1);
        assertThat(cart.get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("addItem adds a second distinct product as a new entry")
    void addItem_addsSecondProduct_asNewEntry() {
        // Given: the cart has one item (laptop)
        List<CartItem> existingCart = new ArrayList<>();
        existingCart.add(new CartItem("p1", "Laptop", new BigDecimal("999.99"), 1));
        when(session.getAttribute(CartService.CART_SESSION_KEY)).thenReturn(existingCart);

        // When: a different product (mouse) is added
        List<CartItem> cart = cartService.addItem(mouseRequest, session);

        // Then: the cart has two distinct items
        assertThat(cart).hasSize(2);
        assertThat(cart).extracting(CartItem::getProductId)
                .containsExactlyInAnyOrder("p1", "p2");
    }

    // ── removeItem ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeItem removes the matching product from the cart")
    void removeItem_removesMatchingProduct() {
        // Given: the cart has two items
        List<CartItem> existingCart = new ArrayList<>();
        existingCart.add(new CartItem("p1", "Laptop", new BigDecimal("999.99"), 1));
        existingCart.add(new CartItem("p2", "Mouse",  new BigDecimal("29.99"),  2));
        when(session.getAttribute(CartService.CART_SESSION_KEY)).thenReturn(existingCart);

        // When: the laptop (p1) is removed
        List<CartItem> cart = cartService.removeItem("p1", session);

        // Then: only the mouse remains
        assertThat(cart).hasSize(1);
        assertThat(cart.get(0).getProductId()).isEqualTo("p2");

        // And: the updated cart was saved to the session
        verify(session).setAttribute(eq(CartService.CART_SESSION_KEY), any());
    }

    @Test
    @DisplayName("removeItem is a no-op when the product is not in the cart")
    void removeItem_isNoOp_whenProductNotInCart() {
        // Given: the cart has one item
        List<CartItem> existingCart = new ArrayList<>();
        existingCart.add(new CartItem("p1", "Laptop", new BigDecimal("999.99"), 1));
        when(session.getAttribute(CartService.CART_SESSION_KEY)).thenReturn(existingCart);

        // When: removing a product ID that does not exist in the cart
        List<CartItem> cart = cartService.removeItem("p999", session);

        // Then: the cart is unchanged
        assertThat(cart).hasSize(1);
        assertThat(cart.get(0).getProductId()).isEqualTo("p1");
    }

    // ── clearCart ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clearCart replaces the cart with an empty list")
    void clearCart_replacesCartWithEmptyList() {
        // Given: a session (no need to stub getAttribute — clearCart writes directly)

        // When
        cartService.clearCart(session);

        // Then: setAttribute was called with an empty list
        verify(session).setAttribute(eq(CartService.CART_SESSION_KEY), eq(new ArrayList<>()));
    }

    // ── getSessionId ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSessionId returns the session ID from the HttpSession")
    void getSessionId_returnsSessionId() {
        // Given: the mocked session returns a specific ID
        when(session.getId()).thenReturn("test-session-id-abc123");

        // When
        String id = cartService.getSessionId(session);

        // Then: the same ID is returned
        assertThat(id).isEqualTo("test-session-id-abc123");
    }
}
