package com.example.liquibasemigrations;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test for the Spring Boot application context.
 *
 * <p>
 * This test verifies that the entire application context loads successfully,
 * which implicitly confirms that:
 * </p>
 * <ul>
 * <li>All Spring beans are correctly configured.</li>
 * <li>Liquibase can connect to the H2 test database.</li>
 * <li>All changelogs execute without errors.</li>
 * <li>Hibernate can validate the entity schema against the migrated
 * tables.</li>
 * </ul>
 *
 * <p>
 * The test profile uses H2 in-memory database (configured in
 * {@code src/test/resources/application.properties}), so no real PostgreSQL
 * instance is required to run this test.
 * </p>
 */
@SpringBootTest
class LiquibaseMigrationsApplicationTests {

    /**
     * If the application context loads without throwing an exception,
     * this test passes. It is the simplest and most comprehensive smoke test.
     */
    @Test
    void contextLoads() {
        // The assertion is implicit: Spring throws an exception if context fails.
    }

}
