package com.example.redissessionstore.controller;

import com.example.redissessionstore.domain.CartItem;
import com.example.redissessionstore.dto.CartItemRequest;
import com.example.redissessionstore.service.CartService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing the shopping-cart API.
 *
 * <p>All endpoints receive an {@link HttpSession} argument which Spring MVC resolves
 * automatically from the current request. Because Spring Session is active, this
 * session is backed by Redis — the controller is completely unaware of this and uses
 * only the standard {@code jakarta.servlet} API.
 *
 * <p>A session cookie ({@code SESSION} by default with Spring Session) is sent back
 * to the client on the first request and echoed on all subsequent requests, allowing
 * the server to locate the correct Redis session for each user.
 *
 * <p>Base path: {@code /api/cart}
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    /**
     * Constructor injection makes the dependency explicit and simplifies testing
     * without needing to load the full Spring application context.
     */
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // ── GET /api/cart ─────────────────────────────────────────────────────────────

    /**
     * Retrieve the current session's shopping cart.
     *
     * <p>If the client is new (no session cookie) Spring Session creates a new Redis
     * session automatically and an empty cart list is returned.
     *
     * @param session the current HTTP session (resolved by Spring MVC)
     * @return 200 OK with the list of cart items (may be empty)
     */
    @GetMapping
    public ResponseEntity<List<CartItem>> getCart(HttpSession session) {
        List<CartItem> cart = cartService.getCart(session);
        return ResponseEntity.ok(cart);
    }

    // ── GET /api/cart/total ───────────────────────────────────────────────────────

    /**
     * Return the total price (sum of line totals) for the current cart.
     *
     * @param session the current HTTP session
     * @return 200 OK with a JSON object containing the {@code total} field
     */
    @GetMapping("/total")
    public ResponseEntity<Map<String, BigDecimal>> getCartTotal(HttpSession session) {
        BigDecimal total = cartService.getCartTotal(session);
        // Wrap in a map so the response is a JSON object {"total": 99.99}
        return ResponseEntity.ok(Map.of("total", total));
    }

    // ── GET /api/cart/session-info ────────────────────────────────────────────────

    /**
     * Return diagnostic information about the current HTTP session.
     *
     * <p>Exposes the session ID and the number of items in the cart. Useful for
     * demonstrating that two clients with different session cookies have independent
     * carts in Redis.
     *
     * @param session the current HTTP session
     * @return 200 OK with a JSON object containing session metadata
     */
    @GetMapping("/session-info")
    public ResponseEntity<Map<String, Object>> getSessionInfo(HttpSession session) {
        return ResponseEntity.ok(Map.of(
                "sessionId",    cartService.getSessionId(session),
                "cartSize",     cartService.getCart(session).size(),
                "cartTotal",    cartService.getCartTotal(session),
                // maxInactiveInterval is set in SessionConfig (1800 seconds = 30 min)
                "maxInactiveIntervalSeconds", session.getMaxInactiveInterval()
        ));
    }

    // ── POST /api/cart/items ──────────────────────────────────────────────────────

    /**
     * Add an item to the session cart (or increment its quantity if already present).
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body before the
     * method is invoked. Invalid requests receive a 400 Bad Request response with
     * field-level error details provided by Spring Boot's default error handler.
     *
     * @param request the item to add, validated from the JSON request body
     * @param session the current HTTP session
     * @return 200 OK with the updated cart
     */
    @PostMapping("/items")
    public ResponseEntity<List<CartItem>> addItem(
            @Valid @RequestBody CartItemRequest request,
            HttpSession session) {

        List<CartItem> updatedCart = cartService.addItem(request, session);
        return ResponseEntity.ok(updatedCart);
    }

    // ── DELETE /api/cart/items/{productId} ────────────────────────────────────────

    /**
     * Remove a specific item from the cart by its product ID.
     *
     * <p>If the product ID is not in the cart, the operation is a no-op and the
     * current cart is returned unchanged.
     *
     * @param productId the product ID to remove (from the URL path)
     * @param session   the current HTTP session
     * @return 200 OK with the updated cart
     */
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<List<CartItem>> removeItem(
            @PathVariable String productId,
            HttpSession session) {

        List<CartItem> updatedCart = cartService.removeItem(productId, session);
        return ResponseEntity.ok(updatedCart);
    }

    // ── DELETE /api/cart ──────────────────────────────────────────────────────────

    /**
     * Clear all items from the current session's cart.
     *
     * <p>This does NOT invalidate the session itself — the session remains active
     * (the Redis key is preserved) but the {@code cart} attribute is reset to an
     * empty list.
     *
     * @param session the current HTTP session
     * @return 204 No Content on success
     */
    @DeleteMapping
    public ResponseEntity<Void> clearCart(HttpSession session) {
        cartService.clearCart(session);
        return ResponseEntity.noContent().build();
    }

    // ── DELETE /api/cart/session ──────────────────────────────────────────────────

    /**
     * Invalidate the current session entirely (equivalent to "log out").
     *
     * <p>Spring Session's filter intercepts the invalidation call and deletes the
     * corresponding Redis key so no stale data remains in Redis.
     *
     * @param session the current HTTP session
     * @return 204 No Content on success
     */
    @DeleteMapping("/session")
    public ResponseEntity<Void> invalidateSession(HttpSession session) {
        // session.invalidate() tells Spring Session to remove the Redis key
        session.invalidate();
        return ResponseEntity.noContent().build();
    }
}
