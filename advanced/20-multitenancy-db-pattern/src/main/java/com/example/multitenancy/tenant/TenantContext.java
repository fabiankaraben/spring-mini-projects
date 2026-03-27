package com.example.multitenancy.tenant;

/**
 * Thread-local holder for the current tenant identifier.
 *
 * <p>This is the cornerstone of the schema-per-tenant approach. Java's
 * {@link ThreadLocal} binds a value to the current OS thread, so every
 * request handled by a Servlet thread carries its own independent tenant ID
 * without sharing state between concurrent requests.</p>
 *
 * <p><b>Lifecycle:</b></p>
 * <ol>
 *   <li>{@link com.example.multitenancy.web.interceptor.TenantInterceptor} reads
 *       the {@code X-Tenant-ID} HTTP header and calls {@link #setCurrentTenant(String)}
 *       at the start of each request.</li>
 *   <li>{@link com.example.multitenancy.config.TenantRoutingDataSource} calls
 *       {@link #getCurrentTenant()} when Spring Data JPA needs a database connection,
 *       and routes the connection to the correct PostgreSQL schema.</li>
 *   <li>{@link com.example.multitenancy.web.interceptor.TenantInterceptor} calls
 *       {@link #clear()} in its {@code afterCompletion} callback to prevent
 *       tenant ID leakage to the next request reusing the same thread (thread-pool
 *       threads are reused).</li>
 * </ol>
 *
 * <p><b>Why ThreadLocal?</b> Spring MVC processes each HTTP request on a single
 * Servlet thread from start to finish (synchronous, blocking I/O). A ThreadLocal
 * is the simplest and most efficient way to propagate per-request context through
 * the call stack without passing parameters everywhere.</p>
 *
 * <p><b>Caution:</b> In reactive / non-blocking stacks (e.g. Spring WebFlux),
 * a single request may hop across threads, making ThreadLocal unreliable.
 * For reactive use-cases, use Reactor's {@code Context} instead.</p>
 */
public final class TenantContext {

    /**
     * Thread-local that stores the current tenant ID for the executing thread.
     *
     * <p>Using {@code InheritableThreadLocal} would propagate the value to child
     * threads, which could cause unintended schema access in async tasks. We
     * intentionally use a plain {@code ThreadLocal} to keep isolation strict.</p>
     */
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    /** Prevent instantiation — this class is a static utility. */
    private TenantContext() {}

    /**
     * Returns the tenant ID bound to the current thread.
     *
     * @return the current tenant ID, or {@code null} if none has been set
     *         (e.g. during application startup or health-check requests that
     *         do not include an {@code X-Tenant-ID} header).
     */
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Binds the given tenant ID to the current thread.
     *
     * <p>Must always be paired with a call to {@link #clear()} at the end of
     * the request to avoid leaking tenant state to subsequent requests on the
     * same thread.</p>
     *
     * @param tenantId the identifier of the tenant for this request; must not be
     *                 {@code null} or blank for any tenant-scoped operation.
     */
    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Removes the tenant ID bound to the current thread.
     *
     * <p>Must be called at the end of every request (in a {@code finally} block
     * or an interceptor's {@code afterCompletion} callback) to prevent stale
     * tenant IDs from being used by the next request handled by this thread.</p>
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
