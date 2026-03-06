package com.example.mustache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: verifies that the Spring application context loads successfully.
 *
 * <p>
 * {@code @SpringBootTest} starts the full application context (all beans,
 * auto-configuration, embedded Tomcat, Mustache ViewResolver, etc.).
 * If any bean definition is invalid or a required dependency is missing,
 * the context will fail to start and this test will fail.
 * </p>
 *
 * <p>
 * This is the lightest possible integration test — it does not make any HTTP
 * requests. Its sole purpose is to catch context-startup failures early.
 * </p>
 */
@SpringBootTest
class MustacheTemplatesApplicationTests {

    @Test
    void contextLoads() {
        // If the application context starts without throwing an exception,
        // this test passes. No explicit assertions needed.
    }
}
