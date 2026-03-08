package com.example.banner;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for BannerService.
 * <p>
 * This is a pure JUnit 5 test that doesn't require the Spring Context.
 * It tests the business logic of the service in isolation.
 * </p>
 */
class BannerServiceTest {

    private final BannerService bannerService = new BannerService();

    @Test
    void shouldReturnWelcomeMessage() {
        String message = bannerService.getWelcomeMessage();
        assertEquals("Application started successfully! Check the console for the custom banner.", message);
    }
}
