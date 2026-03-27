package com.example.multitenancy.web.interceptor;

import com.example.multitenancy.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that extracts the current tenant from the HTTP request.
 *
 * <p>This interceptor is the entry point of the multi-tenancy pipeline. It reads
 * the {@value #TENANT_HEADER} header from every incoming HTTP request and stores
 * the value in {@link TenantContext} (a thread-local). From that point on, any
 * component in the same request thread (e.g., JPA repositories) can call
 * {@link TenantContext#getCurrentTenant()} to learn which tenant is active.</p>
 *
 * <h2>Request pipeline</h2>
 * <pre>
 *   Client
 *     │  GET /api/products
 *     │  X-Tenant-ID: tenant_alpha
 *     ▼
 *   TenantInterceptor.preHandle()
 *     │  TenantContext.setCurrentTenant("tenant_alpha")
 *     ▼
 *   ProductController.list()
 *     │  productRepository.findAll()
 *     ▼
 *   TenantIdentifierResolver.resolveCurrentTenantIdentifier()
 *     │  returns "tenant_alpha"
 *     ▼
 *   TenantConnectionProvider.getConnection("tenant_alpha")
 *     │  SET search_path TO tenant_alpha, public
 *     ▼
 *   PostgreSQL — executes SELECT in schema tenant_alpha
 * </pre>
 *
 * <p>The interceptor is registered in
 * {@link com.example.multitenancy.config.WebMvcConfig}.</p>
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);

    /**
     * The HTTP header name used to identify the tenant for each request.
     *
     * <p>Clients must include this header in every API request. Example:
     * {@code X-Tenant-ID: tenant_alpha}</p>
     */
    public static final String TENANT_HEADER = "X-Tenant-ID";

    /**
     * Extracts the tenant ID from the request and stores it in {@link TenantContext}.
     *
     * <p>This method runs <em>before</em> the request reaches any controller. If the
     * {@value #TENANT_HEADER} header is present and non-blank, the tenant is set in
     * the thread-local context. If the header is missing, the context is left empty
     * and the request proceeds — downstream code (e.g., the controller) may then
     * reject the request with a 400 Bad Request.</p>
     *
     * <p>Actuator endpoints ({@code /actuator/**}) are excluded from tenant
     * resolution in {@link com.example.multitenancy.config.WebMvcConfig} so that
     * health checks work without an {@code X-Tenant-ID} header.</p>
     *
     * @param request  the current HTTP request
     * @param response the current HTTP response
     * @param handler  the chosen handler for this request
     * @return {@code true} always — we never block the request here; missing tenant
     *         validation is delegated to the controller layer.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId != null && !tenantId.isBlank()) {
            log.debug("Resolved tenant '{}' from header '{}'", tenantId, TENANT_HEADER);
            TenantContext.setCurrentTenant(tenantId.trim().toLowerCase());
        } else {
            log.debug("No tenant header '{}' found in request to '{}'", TENANT_HEADER, request.getRequestURI());
        }
        return true; // always continue processing
    }

    /**
     * Clears the tenant context after the request has been fully processed.
     *
     * <p>This cleanup is critical: servlet threads are reused across requests (thread
     * pool). If we do not clear the {@link ThreadLocal} here, the next request
     * handled by this thread will inherit the tenant ID of the previous request —
     * a serious data isolation bug.</p>
     *
     * <p>Using {@code afterCompletion} (rather than {@code postHandle}) guarantees
     * this runs even if the controller throws an exception.</p>
     *
     * @param request   the current HTTP request
     * @param response  the current HTTP response
     * @param handler   the handler that processed the request
     * @param ex        any exception thrown by the handler, or {@code null}
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        log.debug("Clearing tenant context after request to '{}'", request.getRequestURI());
        TenantContext.clear();
    }
}
