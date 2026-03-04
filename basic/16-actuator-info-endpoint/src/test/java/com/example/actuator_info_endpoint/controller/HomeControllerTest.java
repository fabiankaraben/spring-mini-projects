package com.example.actuator_info_endpoint.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sliced integration test mapping exclusively the Web/MVC layer of the
 * application.
 * Focuses purely on testing the HomeController without spinning up Actuator
 * specifics or Full Server containers.
 * This satisfies the @WebMvcTest educational requirement.
 */
@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should issue a 302 Redirection to the actuator info URL when accessing Root Path")
    void givenRootAccess_ShouldRedirectToActuatorInfo() throws Exception {
        // We simulate a GET call to "/" and assert that an HTTP 302 redirect happens
        // verifying it routes specifically to /actuator/info
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/actuator/info"));
    }
}
