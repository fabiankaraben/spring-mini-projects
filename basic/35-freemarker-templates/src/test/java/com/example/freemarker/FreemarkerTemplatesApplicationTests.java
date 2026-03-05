package com.example.freemarker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test that verifies the Spring application context starts successfully.
 *
 * <p>
 * {@code @SpringBootTest} loads the <em>full</em> application context, exactly
 * as it would be in production. The test has no assertions — if the context
 * fails to start (e.g. due to a misconfigured bean or a missing dependency),
 * the test fails automatically.
 * </p>
 *
 * <p>
 * This is sometimes called a "context loads" test. It is a lightweight sanity
 * check that catches wiring errors early without the overhead of spinning up
 * an HTTP server.
 * </p>
 */
@SpringBootTest
class FreemarkerTemplatesApplicationTests {

    /**
     * Verifies that the Spring context starts without throwing an exception.
     * No explicit assertion is needed — the test framework considers an
     * uncaught exception a test failure.
     */
    @Test
    void contextLoads() {
        // No assertions required: if the context fails to load,
        // Spring throws an exception and the test fails automatically.
    }
}
