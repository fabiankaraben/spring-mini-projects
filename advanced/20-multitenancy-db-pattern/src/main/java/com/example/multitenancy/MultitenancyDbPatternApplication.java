package com.example.multitenancy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Multi-Tenancy DB Pattern application.
 *
 * <p>This application demonstrates a "schema-per-tenant" multi-tenancy strategy using
 * Spring Boot, Spring Data JPA, and PostgreSQL. Each tenant is isolated in its own
 * PostgreSQL schema (e.g., "tenant_alpha", "tenant_beta"). The active tenant is
 * resolved from the incoming HTTP request header {@code X-Tenant-ID} and stored in a
 * thread-local context. A custom {@link org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource}
 * (see {@code TenantRoutingDataSource}) then uses that context to route every database
 * connection to the correct tenant schema via the PostgreSQL {@code search_path}.</p>
 *
 * <h2>Key components</h2>
 * <ul>
 *   <li><b>TenantContext</b>           — thread-local storage for the current tenant ID.</li>
 *   <li><b>TenantRoutingDataSource</b> — Spring's AbstractRoutingDataSource subclass that
 *       reads the tenant ID and adjusts the schema search path.</li>
 *   <li><b>TenantInterceptor</b>       — Spring MVC HandlerInterceptor that extracts the
 *       {@code X-Tenant-ID} header and populates TenantContext.</li>
 *   <li><b>DataSourceConfig</b>        — Spring @Configuration that creates the routing
 *       data source and registers the per-tenant connection wrappers.</li>
 *   <li><b>Product / ProductRepository</b> — simple domain entity and JPA repository.</li>
 *   <li><b>ProductController</b>       — REST API (CRUD) for Products.</li>
 * </ul>
 */
@SpringBootApplication
public class MultitenancyDbPatternApplication {

	public static void main(String[] args) {
		SpringApplication.run(MultitenancyDbPatternApplication.class, args);
	}
}
