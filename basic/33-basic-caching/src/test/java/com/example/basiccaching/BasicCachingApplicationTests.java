package com.example.basiccaching;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test that verifies the Spring application context loads successfully.
 *
 * <p>
 * This is the most basic form of integration test. If the context fails to load
 * (e.g., missing beans, misconfiguration), this test will fail and alert us
 * early.
 * </p>
 */
@SpringBootTest
class BasicCachingApplicationTests {

    @Test
    void contextLoads() {
        // If the application context starts without throwing, the test passes.
        // No explicit assertion is needed here.
    }
}
