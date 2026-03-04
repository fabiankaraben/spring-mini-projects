package com.example.profileconfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test case designed to ensure the application context loads properly
 * when the 'dev' profile is activated.
 */
@SpringBootTest
@ActiveProfiles("dev") // Activates the dev profile for this test runtime
class ProfileSpecificConfigApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads_withDevProfile() {
        // Verify that the context can load without any errors
        assertNotNull(applicationContext, "Application context should not be null");

        // Assert that the proper bean (DevMessageService) is present in the context
        assertTrue(applicationContext.containsBean("devMessageService"),
                "The 'devMessageService' bean should be loaded when the 'dev' profile is active");
    }

}
