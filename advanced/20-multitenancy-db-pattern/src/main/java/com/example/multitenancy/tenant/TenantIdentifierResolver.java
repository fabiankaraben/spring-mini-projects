package com.example.multitenancy.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Hibernate hook that resolves the current tenant identifier.
 *
 * <p>Hibernate's multi-tenancy support requires an implementation of
 * {@link CurrentTenantIdentifierResolver} to know which tenant is active when
 * it needs to open a database connection. This implementation simply delegates
 * to {@link TenantContext} — which was populated earlier in the request pipeline
 * by {@link com.example.multitenancy.web.interceptor.TenantInterceptor}.</p>
 *
 * <p>This resolver is registered with Hibernate via
 * {@link com.example.multitenancy.config.DataSourceConfig} through the
 * {@code hibernate.tenant_identifier_resolver} property.</p>
 *
 * <h2>How the pieces fit together</h2>
 * <pre>
 *   HTTP request
 *       │
 *       ▼
 *   TenantInterceptor        ← reads X-Tenant-ID header
 *       │  sets TenantContext
 *       ▼
 *   Spring Data JPA
 *       │  calls Hibernate
 *       ▼
 *   TenantIdentifierResolver ← reads from TenantContext
 *       │  returns tenant ID
 *       ▼
 *   TenantConnectionProvider ← opens schema-scoped connection
 *       │
 *       ▼
 *   PostgreSQL schema "tenant_alpha" or "tenant_beta"
 * </pre>
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    /**
     * The tenant ID that is returned when no tenant is bound to the current thread.
     *
     * <p>This value is used for system-level operations such as schema initialization,
     * health checks, and application startup — where no HTTP request (and therefore
     * no {@code X-Tenant-ID} header) is in scope.</p>
     */
    public static final String DEFAULT_TENANT = "public";

    /**
     * Returns the tenant identifier for the current thread.
     *
     * <p>Called by Hibernate each time it needs a connection to determine which
     * multi-tenant context to use. We read the value that
     * {@link com.example.multitenancy.web.interceptor.TenantInterceptor} stored in
     * {@link TenantContext}. If no tenant is set (e.g., during startup), we fall back
     * to {@link #DEFAULT_TENANT} so that Hibernate can still operate.</p>
     *
     * @return the current tenant ID, or {@value #DEFAULT_TENANT} as a safe fallback.
     */
    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.getCurrentTenant();
        return (tenantId != null && !tenantId.isBlank()) ? tenantId : DEFAULT_TENANT;
    }

    /**
     * Indicates whether Hibernate should validate tenant IDs against the resolver.
     *
     * <p>Returning {@code true} means Hibernate will always call
     * {@link #resolveCurrentTenantIdentifier()} even when opening a session without
     * an explicit tenant, ensuring our routing logic is always consulted.</p>
     *
     * @return {@code true} to always validate
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
