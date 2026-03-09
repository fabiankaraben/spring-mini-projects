package com.example.redissessionstore.service;

import com.example.redissessionstore.domain.CartItem;
import com.example.redissessionstore.dto.CartItemRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that manages a shopping cart stored entirely in the HTTP session.
 *
 * <p>This class demonstrates the core concept of Spring Session: every read and write
 * to {@link HttpSession} is transparently backed by Redis. The service does not know
 * or care that the session data lives in Redis — it simply calls the standard
 * {@code javax.servlet} / {@code jakarta.servlet} session API and Spring Session's
 * filter handles the persistence layer.
 *
 * <p>The shopping cart is stored as a single session attribute under the key
 * {@code "cart"} whose value is a {@code List<CartItem>}. This pattern is typical
 * for lightweight per-user state that does not need a database row (e.g. wish-lists,
 * wizard steps, recently-viewed items).
 *
 * <p>Why Redis instead of the default in-memory session store?
 * <ul>
 *   <li><strong>Survives server restarts</strong> – data lives in Redis, not the JVM heap.</li>
 *   <li><strong>Horizontal scaling</strong> – any instance in a load-balanced cluster
 *       reads the same Redis session, so users are not pinned to a specific server.</li>
 *   <li><strong>Automatic TTL</strong> – Redis expires sessions after the configured
 *       {@code maxInactiveIntervalInSeconds}, with no cleanup job required.</li>
 * </ul>
 */
@Service
public class CartService {

    /**
     * The session attribute key under which the cart list is stored.
     * Using a constant prevents typos when reading and writing the attribute.
     */
    public static final String CART_SESSION_KEY = "cart";

    // ── Cart read operations ──────────────────────────────────────────────────────

    /**
     * Retrieve the current cart from the session.
     *
     * <p>If no cart attribute exists yet (first request), an empty list is returned
     * without modifying the session. The cart is only saved to the session (and
     * therefore to Redis) when an item is added.
     *
     * @param session the current HTTP session (provided by Spring MVC)
     * @return the list of items in the cart; never null
     */
    public List<CartItem> getCart(HttpSession session) {
        // getAttribute returns null when the attribute does not exist yet
        List<CartItem> cart = getCartFromSession(session);
        return cart;
    }

    /**
     * Calculate the total price of all items in the cart.
     *
     * @param session the current HTTP session
     * @return sum of (price × quantity) for every item; {@code BigDecimal.ZERO} if empty
     */
    public BigDecimal getCartTotal(HttpSession session) {
        // Reduce the list of items by summing their line totals
        return getCartFromSession(session).stream()
                .map(CartItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Cart write operations ─────────────────────────────────────────────────────

    /**
     * Add an item to the cart or increase its quantity if it already exists.
     *
     * <p>If a {@link CartItem} with the same {@code productId} is already in the cart,
     * its quantity is incremented by the amount in the request rather than adding a
     * second entry. This keeps the cart list clean (one entry per product).
     *
     * <p>After modifying the list the updated cart is written back to the session via
     * {@code session.setAttribute(CART_SESSION_KEY, cart)}. Spring Session then
     * serialises the value to Redis asynchronously before the response is sent.
     *
     * @param request the item to add (validated by the controller)
     * @param session the current HTTP session
     * @return the updated cart
     */
    public List<CartItem> addItem(CartItemRequest request, HttpSession session) {
        List<CartItem> cart = getCartFromSession(session);

        // Check whether this product is already in the cart
        CartItem existing = cart.stream()
                .filter(item -> item.getProductId().equals(request.getProductId()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            // Item already in cart – just increment the quantity
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
        } else {
            // New product – add a fresh CartItem to the list
            cart.add(new CartItem(
                    request.getProductId(),
                    request.getProductName(),
                    request.getPrice(),
                    request.getQuantity()
            ));
        }

        // Persist the updated cart back to the session (triggers a Redis write)
        session.setAttribute(CART_SESSION_KEY, cart);
        return cart;
    }

    /**
     * Remove an item from the cart by its product ID.
     *
     * <p>If no item with the given {@code productId} exists the cart is left unchanged.
     *
     * @param productId the ID of the product to remove
     * @param session   the current HTTP session
     * @return the updated cart (may be empty if the last item was removed)
     */
    public List<CartItem> removeItem(String productId, HttpSession session) {
        List<CartItem> cart = getCartFromSession(session);

        // Remove the item whose productId matches; no-op if not present
        cart.removeIf(item -> item.getProductId().equals(productId));

        // Write the updated cart back to Redis
        session.setAttribute(CART_SESSION_KEY, cart);
        return cart;
    }

    /**
     * Clear all items from the cart.
     *
     * <p>The session attribute is set to an empty list (the session itself remains
     * active — only the cart contents are cleared). To also invalidate the session
     * entirely, callers should invoke {@link HttpSession#invalidate()} directly.
     *
     * @param session the current HTTP session
     */
    public void clearCart(HttpSession session) {
        // Overwrite the cart attribute with an empty list
        session.setAttribute(CART_SESSION_KEY, new ArrayList<>());
    }

    // ── Session introspection helpers ─────────────────────────────────────────────

    /**
     * Return the current session's ID.
     *
     * <p>This is the same value that Spring Session stores as a Redis key (under the
     * pattern {@code spring:session:sessions:<id>}). Useful for diagnostics and for
     * writing integration tests that verify Redis key existence.
     *
     * @param session the current HTTP session
     * @return the session ID string
     */
    public String getSessionId(HttpSession session) {
        return session.getId();
    }

    // ── Private helpers ───────────────────────────────────────────────────────────

    /**
     * Retrieve the cart list from the session, creating an empty one if absent.
     *
     * <p>Using a helper keeps {@code getCart} and {@code addItem} from duplicating
     * the same null-check / cast logic.
     *
     * @param session the current HTTP session
     * @return the existing cart list, or a new empty list stored in the session
     */
    @SuppressWarnings("unchecked")
    private List<CartItem> getCartFromSession(HttpSession session) {
        Object attribute = session.getAttribute(CART_SESSION_KEY);
        if (attribute == null) {
            // Lazily initialise the cart the first time it is accessed
            List<CartItem> newCart = new ArrayList<>();
            session.setAttribute(CART_SESSION_KEY, newCart);
            return newCart;
        }
        // The cast is safe because we only ever store List<CartItem> under this key
        return (List<CartItem>) attribute;
    }
}
