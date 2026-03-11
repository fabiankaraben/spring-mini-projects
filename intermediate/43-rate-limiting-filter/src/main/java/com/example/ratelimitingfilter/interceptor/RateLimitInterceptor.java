package com.example.ratelimitingfilter.interceptor;

import com.example.ratelimitingfilter.config.RateLimitProperties;
import com.example.ratelimitingfilter.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that enforces per-client rate limiting on every
 * incoming HTTP request.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Before a request reaches the controller,
 *       {@link #preHandle(HttpServletRequest, HttpServletResponse, Object)} is
 *       called by the {@link org.springframework.web.servlet.DispatcherServlet}.</li>
 *   <li>The interceptor extracts the client IP address from the request.</li>
 *   <li>It asks {@link RateLimiterService} whether the client has tokens left in
 *       their bucket.</li>
 *   <li>If tokens are available the request proceeds; otherwise a
 *       {@code 429 Too Many Requests} response is returned immediately and the
 *       controller is never invoked.</li>
 * </ol>
 *
 * <h2>Response headers</h2>
 * <ul>
 *   <li>{@code X-RateLimit-Limit}     – bucket capacity (from config)</li>
 *   <li>{@code X-RateLimit-Remaining} – tokens left after this request</li>
 *   <li>{@code Retry-After}           – seconds until the bucket refills
 *       (only on 429 responses)</li>
 * </ul>
 *
 * <p>This interceptor is registered in
 * {@link com.example.ratelimitingfilter.config.WebMvcConfig}.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties props;

    public RateLimitInterceptor(RateLimiterService rateLimiterService,
                                RateLimitProperties props) {
        this.rateLimiterService = rateLimiterService;
        this.props = props;
    }

    /**
     * Called before the controller handles the request.
     *
     * @param request  current HTTP request
     * @param response current HTTP response
     * @param handler  chosen handler to execute (not used here)
     * @return {@code true} to proceed; {@code false} to abort (rate limited)
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // Derive the client identifier from the remote IP address.
        // getRemoteAddr() returns the direct network peer.  If the application
        // sits behind a reverse proxy, consider reading "X-Forwarded-For" instead.
        String clientKey = resolveClientKey(request);

        // Always inform the client of the configured limit
        response.setHeader("X-RateLimit-Limit", String.valueOf(props.getCapacity()));

        boolean allowed = rateLimiterService.tryConsume(clientKey);

        if (allowed) {
            // Expose the remaining token count after this consumption
            int remaining = rateLimiterService.getRemainingTokens(clientKey);
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            return true;    // proceed to the controller
        }

        // Bucket is empty – reject with 429
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("Retry-After", String.valueOf(props.getRefillPeriodSeconds()));
        response.setStatus(HttpServletResponse.SC_REQUEST_TIMEOUT); // 408 is wrong; use 429
        // HttpServletResponse doesn't have a constant for 429 – set the status code directly
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("""
                {
                  "status": 429,
                  "error": "Too Many Requests",
                  "message": "Rate limit exceeded. Please retry after %d seconds."
                }
                """.formatted(props.getRefillPeriodSeconds()));
        return false;       // abort the request – controller is never called
    }

    /**
     * Resolves the client identifier from the HTTP request.
     *
     * <p>The strategy here is:
     * <ol>
     *   <li>Check for {@code X-Forwarded-For} (set by load balancers / proxies).
     *       If present, use the first (leftmost) IP address in the list.</li>
     *   <li>Fall back to {@link HttpServletRequest#getRemoteAddr()}.</li>
     * </ol>
     *
     * @param request incoming HTTP request
     * @return a non-null string that identifies the caller
     */
    protected String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a comma-separated chain of IPs;
            // the first one is the original client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
