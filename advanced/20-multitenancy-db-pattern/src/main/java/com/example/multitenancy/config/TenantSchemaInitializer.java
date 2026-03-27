package com.example.multitenancy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Application startup component that initializes the database schemas for all configured tenants.
 *
 * <p>On every application start, this component:</p>
 * <ol>
 *   <li>Reads the list of tenant IDs from the {@code app.tenants} configuration property
 *       (via {@link AppProperties}).</li>
 *   <li>For each tenant, creates a dedicated PostgreSQL schema (e.g., {@code tenant_alpha})
 *       if it does not already exist.</li>
 *   <li>Executes the {@code schema.sql} DDL script within each tenant's schema to create
 *       (or ensure existence of) the required tables.</li>
 * </ol>
 *
 * <h2>Why not use Hibernate's DDL auto-generation?</h2>
 * <p>Hibernate's {@code hbm2ddl.auto = create-drop} or {@code update} would run against
 * a single DataSource and a single schema. For multi-tenant schema creation, we need to
 * explicitly loop through each tenant's schema and apply DDL separately. Using explicit
 * SQL scripts gives us full control over schema management and makes the initialization
 * deterministic and repeatable.</p>
 *
 * <h2>Why use AppProperties instead of @Value?</h2>
 * <p>Spring's {@code @Value("${app.tenants}")} annotation works with comma-separated
 * strings but does NOT reliably bind YAML lists ({@code - item}) to {@code List<String>}.
 * Using {@link AppProperties} (annotated with {@code @ConfigurationProperties(prefix = "app")})
 * correctly handles both YAML sequences and comma-separated environment variables, making
 * the application usable in both local YAML config and Docker Compose env-var config.</p>
 *
 * <h2>Idempotency</h2>
 * <p>All DDL uses {@code IF NOT EXISTS} (via {@code CREATE SCHEMA IF NOT EXISTS} and
 * the schema.sql script), so re-running on an already-initialized database is safe.</p>
 */
@Component
public class TenantSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaInitializer.class);

    /**
     * Raw JDBC template against the shared DataSource.
     *
     * <p>We use {@link JdbcTemplate} directly (rather than JPA) for schema-level DDL
     * because these statements run before any tenant context is established. Using JPA
     * here would require a valid tenant in the {@link com.example.multitenancy.tenant.TenantContext},
     * which does not exist during startup.</p>
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Typed configuration properties that provide the list of tenant IDs.
     *
     * <p>Bound from the {@code app.*} namespace in {@code application.yml}:</p>
     * <pre>
     * app:
     *   tenants:
     *     - tenant_alpha
     *     - tenant_beta
     * </pre>
     *
     * <p>In Docker Compose, the same list is supplied as a comma-separated env var:
     * {@code APP_TENANTS=tenant_alpha,tenant_beta}. Spring Boot's relaxed binding
     * in {@link AppProperties} handles both formats correctly.</p>
     */
    private final AppProperties appProperties;

    /**
     * Constructs the initializer with its required dependencies.
     *
     * @param jdbcTemplate  the Spring JDBC template for raw SQL execution
     * @param appProperties the typed application configuration properties
     */
    public TenantSchemaInitializer(JdbcTemplate jdbcTemplate, AppProperties appProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.appProperties = appProperties;
    }

    /**
     * Initializes all tenant schemas after the application context is fully started.
     *
     * <p>This method is called by Spring Boot's {@link ApplicationRunner} mechanism
     * immediately after the context is refreshed, before the application starts serving
     * HTTP requests.</p>
     *
     * @param args the application arguments (not used)
     * @throws Exception if schema creation or DDL execution fails
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> tenants = appProperties.getTenants();
        log.info("Initializing schemas for {} tenant(s): {}", tenants.size(), tenants);

        // Load the SQL schema script once (it's the same for all tenants)
        String schemaSql = loadSchemaSql();

        for (String tenant : tenants) {
            initTenantSchema(tenant, schemaSql);
        }

        log.info("All tenant schemas initialized successfully.");
    }

    /**
     * Creates the schema for a single tenant and applies the DDL script.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Validate the tenant identifier to prevent SQL injection.</li>
     *   <li>Create the schema if it does not exist.</li>
     *   <li>Set the search_path to the new schema.</li>
     *   <li>Execute the DDL script (creates tables, indexes, etc.).</li>
     *   <li>Reset the search_path to public.</li>
     * </ol>
     *
     * @param tenant    the tenant identifier (validated before use in SQL)
     * @param schemaSql the DDL SQL to execute in the tenant's schema
     */
    private void initTenantSchema(String tenant, String schemaSql) {
        // Validate tenant ID to prevent SQL injection.
        // The schema name is embedded in SQL strings that cannot use JDBC parameters.
        if (!tenant.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Invalid tenant identifier: '" + tenant + "'. Only alphanumeric and underscores allowed.");
        }

        String schemaName = tenant.toLowerCase();
        log.info("Initializing schema '{}' ...", schemaName);

        // Step 1: Create the schema if it doesn't exist
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        log.debug("Schema '{}' created (or already exists).", schemaName);

        // Step 2: Switch search_path to the tenant schema and apply DDL
        jdbcTemplate.execute("SET search_path TO " + schemaName + ", public");
        jdbcTemplate.execute(schemaSql);
        log.debug("DDL applied to schema '{}'.", schemaName);

        // Step 3: Reset search_path so subsequent JDBC operations use the default
        jdbcTemplate.execute("SET search_path TO public");
    }

    /**
     * Loads the {@code schema.sql} resource from the classpath.
     *
     * <p>This file contains the DDL for the tables that every tenant needs.
     * It is located at {@code src/main/resources/db/schema.sql}.</p>
     *
     * @return the content of schema.sql as a String
     * @throws Exception if the file cannot be read
     */
    private String loadSchemaSql() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/schema.sql");
        try (InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }
}
