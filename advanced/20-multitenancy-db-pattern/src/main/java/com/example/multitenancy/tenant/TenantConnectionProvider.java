package com.example.multitenancy.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hibernate hook that provides schema-scoped JDBC connections for multi-tenancy.
 *
 * <p>Hibernate's multi-tenancy support requires an implementation of
 * {@link MultiTenantConnectionProvider} to know <em>how</em> to get a connection
 * for a given tenant. This implementation uses PostgreSQL's {@code SET search_path}
 * command to switch the active schema on an existing connection from the shared
 * connection pool.</p>
 *
 * <h2>Why SET search_path?</h2>
 * <p>PostgreSQL's {@code search_path} session variable controls which schemas are
 * searched when resolving unqualified table names. By executing
 * {@code SET search_path TO tenant_alpha, public} before handing a connection to
 * Hibernate, all SQL issued through that connection will resolve tables in the
 * {@code tenant_alpha} schema first. This gives perfect schema-level isolation with
 * a single shared database and a single shared connection pool.</p>
 *
 * <h2>Alternative approaches</h2>
 * <ul>
 *   <li><b>Separate DataSources per tenant</b>: stronger isolation but more memory
 *       and connection overhead (one pool per tenant).</li>
 *   <li><b>Separate databases per tenant</b>: strongest isolation, but requires
 *       dynamic DataSource creation and more operational complexity.</li>
 *   <li><b>Row-level discriminator column</b>: no schema switching needed, but
 *       tenants share tables and accidental cross-tenant data leaks are possible
 *       if WHERE clauses are omitted.</li>
 * </ul>
 *
 * <p>The schema-per-tenant approach chosen here strikes a good balance: strong
 * isolation without the overhead of multiple connection pools.</p>
 */
@Component
public class TenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    /**
     * The shared DataSource (backed by HikariCP connection pool).
     * All tenants share the same pool — only the search_path differs.
     */
    private final DataSource dataSource;

    /**
     * Constructs this provider with the application's primary DataSource.
     *
     * @param dataSource the shared connection pool injected by Spring
     */
    public TenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Returns a "generic" connection not scoped to any tenant.
     *
     * <p>Hibernate calls this for schema operations that are not tenant-specific,
     * such as schema validation or DDL execution at startup. We return a raw
     * connection from the pool with the default search_path.</p>
     *
     * @return a JDBC connection from the shared pool
     * @throws SQLException if the connection cannot be obtained
     */
    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Releases a generic (non-tenant-scoped) connection back to the pool.
     *
     * @param connection the connection to release
     * @throws SQLException if releasing fails
     */
    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    /**
     * Returns a JDBC connection scoped to the given tenant schema.
     *
     * <p>The connection is obtained from the shared HikariCP pool (same pool for
     * all tenants). After acquiring it, we execute
     * {@code SET search_path TO <schema>, public} to ensure that all subsequent
     * SQL on this connection resolves tables in the correct tenant schema.</p>
     *
     * <p>Including {@code public} in the search path ensures that shared
     * objects in the public schema (e.g., extensions, helper functions) remain
     * accessible.</p>
     *
     * @param tenantIdentifier the tenant whose schema should be active
     * @return a connection with search_path set to the tenant's schema
     * @throws SQLException if the connection cannot be obtained or the schema
     *                      switch fails
     */
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        // Switch to the tenant's schema by adjusting the PostgreSQL search path.
        // This is the core of the schema-per-tenant routing strategy.
        String schema = toSchemaName(tenantIdentifier);
        connection.createStatement().execute("SET search_path TO " + schema + ", public");
        return connection;
    }

    /**
     * Releases a tenant-scoped connection back to the shared pool.
     *
     * <p>Before returning the connection to the pool we reset the search_path to
     * {@code public} so the next borrower from the pool does not accidentally
     * inherit a stale tenant schema. This is a defensive measure — HikariCP
     * resets connections on return when {@code connectionInitSql} is configured,
     * but an explicit reset here makes the behavior explicit and self-documenting.</p>
     *
     * @param tenantIdentifier the tenant this connection was scoped to
     * @param connection       the connection to release
     * @throws SQLException if releasing fails
     */
    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        // Reset search_path before returning to the pool to prevent schema leakage.
        connection.createStatement().execute("SET search_path TO public");
        connection.close();
    }

    /**
     * Returns {@code false} because we share a single DataSource across all tenants.
     *
     * <p>If this returned {@code true}, Hibernate would assume that different tenant
     * connections are completely separate and would skip the per-connection setup.
     * Since we share a connection pool and differentiate by search_path, we must
     * return {@code false}.</p>
     */
    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    /**
     * Indicates whether the given unwrap target type is supported.
     *
     * @param unwrapType the target type for unwrapping
     * @return {@code false} — this class does not wrap any specific target type
     */
    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    /**
     * Attempts to unwrap this provider as the given type.
     *
     * @param unwrapType the target type
     * @param <T>        the target type
     * @return never returns normally; always throws
     */
    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("Cannot unwrap as " + unwrapType.getName());
    }

    /**
     * Converts a tenant identifier to a PostgreSQL schema name.
     *
     * <p>Tenant identifiers come from HTTP headers (user-controlled input), so we
     * sanitize them by allowing only alphanumeric characters and underscores.
     * This prevents SQL injection via the schema name in the SET search_path
     * statement (which cannot use JDBC parameter binding).</p>
     *
     * @param tenantIdentifier the raw tenant ID from the HTTP header or context
     * @return a sanitized schema name safe to embed in a SQL statement
     * @throws IllegalArgumentException if the identifier contains invalid characters
     */
    static String toSchemaName(String tenantIdentifier) {
        // Validate: only letters, digits, and underscores are allowed.
        // This strictly prevents SQL injection in the SET search_path statement.
        if (!tenantIdentifier.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Invalid tenant identifier: '" + tenantIdentifier + "'. " +
                    "Only alphanumeric characters and underscores are allowed.");
        }
        // Convention: tenant schema names are lowercase (PostgreSQL is case-insensitive
        // for unquoted identifiers, but lowercase is the conventional style).
        return tenantIdentifier.toLowerCase();
    }
}
