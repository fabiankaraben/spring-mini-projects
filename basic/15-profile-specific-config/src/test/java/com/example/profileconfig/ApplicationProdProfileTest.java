package com.example.profileconfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test case to ensure the context behaves properly under the 'prod' profile.
 */
@SpringBootTest
@ActiveProfiles("prod")
class ApplicationProdProfileTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads_withProdProfile() {
        assertNotNull(applicationContext, "Application context should not be null");

        // Assert that the 'prodMessageService' bean is present in the context
        assertTrue(applicationContext.containsBean("prodMessageService"),
                "The 'prodMessageService' bean should be loaded when the 'prod' profile is active");
    }
}
