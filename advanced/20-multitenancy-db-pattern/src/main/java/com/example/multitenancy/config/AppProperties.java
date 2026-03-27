package com.example.multitenancy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Typed configuration properties for the {@code app.*} namespace.
 *
 * <p>Using {@link ConfigurationProperties} instead of {@link org.springframework.beans.factory.annotation.Value}
 * allows Spring Boot to bind a YAML list directly to a {@code List<String>}:</p>
 *
 * <pre>
 * app:
 *   tenants:
 *     - tenant_alpha
 *     - tenant_beta
 * </pre>
 *
 * <p>The {@code @Value("${app.tenants}")} approach only works for comma-separated
 * strings, not YAML sequences. {@code @ConfigurationProperties} handles both formats
 * and provides type safety.</p>
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * The list of tenant identifiers known to this application.
     *
     * <p>At startup, {@link TenantSchemaInitializer} iterates over this list and
     * creates a dedicated PostgreSQL schema for each tenant (if it does not already
     * exist). The list is configured in {@code application.yml} under {@code app.tenants}.</p>
     */
    private List<String> tenants = List.of();

    /**
     * Returns the configured list of tenant identifiers.
     *
     * @return the tenant ID list; never {@code null}, may be empty
     */
    public List<String> getTenants() {
        return tenants;
    }

    /**
     * Sets the list of tenant identifiers.
     * Called by Spring Boot's configuration binding infrastructure.
     *
     * @param tenants the list of tenant IDs from the configuration source
     */
    public void setTenants(List<String> tenants) {
        this.tenants = tenants;
    }
}
