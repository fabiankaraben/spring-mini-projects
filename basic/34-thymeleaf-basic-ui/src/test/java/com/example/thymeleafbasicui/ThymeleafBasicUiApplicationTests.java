package com.example.thymeleafbasicui;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test that verifies the entire Spring application context starts without
 * errors.
 *
 * <p>
 * {@code @SpringBootTest} loads the full application context (all beans,
 * configurations,
 * auto-configurations, etc.). If any bean fails to initialize — for example,
 * due to a
 * missing dependency or a configuration error — this test fails immediately,
 * giving fast
 * feedback that the application is fundamentally broken.
 * </p>
 */
@SpringBootTest
class ThymeleafBasicUiApplicationTests {

    /**
     * An empty test body is intentional. The assertion here is implicit:
     * if the application context loads successfully, the test passes.
     */
    @Test
    void contextLoads() {
        // No assertions needed — the @SpringBootTest annotation is sufficient
        // to verify the application context starts up without errors.
    }
}
