package com.example.eventpublisher.controller;

import com.example.eventpublisher.listener.UserRegistrationListener;
import com.example.eventpublisher.publisher.UserRegistrationPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRegistrationPublisher publisher;

    @MockitoBean
    private UserRegistrationListener listener;

    /**
     * Ensure the POST request routes correctly and triggers the publisher.
     */
    @Test
    void testRegisterUser() throws Exception {
        String testUsername = "fabian";

        mockMvc.perform(post("/api/users/{username}", testUsername))
                .andExpect(status().isOk())
                .andExpect(
                        content().string("Successfully handled initial step and published UserRegistrationEvent for '"
                                + testUsername + "'."));

        verify(publisher).publishUserRegistration(testUsername);
    }

    /**
     * Ensure GET request routes correctly and pulls from listener.
     */
    @Test
    void testFetchProcessedUsers() throws Exception {
        when(listener.getRegisteredUsernames()).thenReturn(List.of("fabian", "alice"));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"fabian\", \"alice\"]"));

        verify(listener).getRegisteredUsernames();
    }
}
