package com.example.multitenancy.config;

import com.example.multitenancy.tenant.TenantConnectionProvider;
import com.example.multitenancy.tenant.TenantIdentifierResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring configuration for the JPA EntityManagerFactory with multi-tenancy support.
 *
 * <h2>Design decision: no custom DataSource bean</h2>
 * <p>We intentionally do NOT define a custom {@code DataSource} bean here. We rely on
 * Spring Boot's DataSource auto-configuration ({@code DataSourceAutoConfiguration})
 * which reads {@code spring.datasource.*} properties including those injected
 * dynamically at test time via {@code @DynamicPropertySource} (Testcontainers).
 * Defining our own {@code @ConfigurationProperties("spring.datasource")} bean would
 * bypass the dynamic property binding and break integration tests.</p>
 *
 * <h2>What we do configure manually</h2>
 * <p>Spring Boot's JPA auto-configuration creates an EntityManagerFactory tied to a
 * single DataSource with no multi-tenancy awareness. To register our custom Hibernate
 * SPI implementations ({@link TenantConnectionProvider} and
 * {@link TenantIdentifierResolver}), we must create the EntityManagerFactory manually
 * and supply those implementations as Hibernate properties.</p>
 */
@Configuration
public class DataSourceConfig {

    /**
     * Creates a JPA vendor adapter backed by Hibernate.
     *
     * <p>The {@link HibernateJpaVendorAdapter} bridges Spring's JPA abstraction
     * with Hibernate-specific configuration. It allows setting Hibernate properties
     * (like the multi-tenancy strategy) through Spring's JPA infrastructure.</p>
     *
     * @param jpaProperties Spring Boot's auto-configured JPA properties
     *                      (reads {@code spring.jpa.*} from application.yml)
     * @return a configured Hibernate JPA vendor adapter
     */
    @Bean
    public JpaVendorAdapter jpaVendorAdapter(JpaProperties jpaProperties) {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        // showSql / formatSql / ddlAuto etc. come from spring.jpa.* properties
        adapter.setShowSql(jpaProperties.isShowSql());
        adapter.setGenerateDdl(jpaProperties.isGenerateDdl());
        if (jpaProperties.getDatabase() != null) {
            adapter.setDatabase(jpaProperties.getDatabase());
        }
        return adapter;
    }

    /**
     * Creates the JPA EntityManagerFactory configured for schema-per-tenant multi-tenancy.
     *
     * <p>The key Hibernate properties set here are:</p>
     * <ul>
     *   <li>{@code hibernate.multiTenancy = SCHEMA} — tells Hibernate to use the
     *       SCHEMA strategy, meaning tenant isolation is at the database-schema level.</li>
     *   <li>{@code hibernate.multi_tenant_connection_provider} — our custom
     *       {@link TenantConnectionProvider} which executes {@code SET search_path}
     *       to switch schemas.</li>
     *   <li>{@code hibernate.tenant_identifier_resolver} — our custom
     *       {@link TenantIdentifierResolver} which reads the current tenant from
     *       {@link com.example.multitenancy.tenant.TenantContext}.</li>
     * </ul>
     *
     * <p>We also forward all other {@code spring.jpa.properties.*} settings so that
     * standard options like {@code hibernate.format_sql} or
     * {@code hibernate.hbm2ddl.auto} continue to work normally.</p>
     *
     * @param dataSource                the shared HikariCP pool
     * @param jpaVendorAdapter          the Hibernate JPA vendor adapter
     * @param jpaProperties             Spring Boot's JPA configuration
     * @param tenantConnectionProvider  routes connections per tenant via SET search_path
     * @param tenantIdentifierResolver  resolves the current tenant from TenantContext
     * @return a fully configured EntityManagerFactory for multi-tenant use
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            JpaVendorAdapter jpaVendorAdapter,
            JpaProperties jpaProperties,
            TenantConnectionProvider tenantConnectionProvider,
            TenantIdentifierResolver tenantIdentifierResolver) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setJpaVendorAdapter(jpaVendorAdapter);

        // Tell Hibernate where to scan for @Entity classes
        em.setPackagesToScan("com.example.multitenancy");

        // Build Hibernate properties map
        Map<String, Object> properties = new HashMap<>(jpaProperties.getProperties());

        // --- Multi-tenancy configuration ---

        // SCHEMA strategy: Hibernate will call the connection provider for every
        // session, passing the tenant identifier so the correct schema is selected.
        properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, tenantConnectionProvider);

        // Hibernate calls this before opening each session to know which tenant is active.
        properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);

        em.setJpaPropertyMap(properties);
        return em;
    }
}
