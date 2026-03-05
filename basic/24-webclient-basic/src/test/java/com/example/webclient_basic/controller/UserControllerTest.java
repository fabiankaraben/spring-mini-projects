package com.example.webclient_basic.controller;

import com.example.webclient_basic.dto.User;
import com.example.webclient_basic.service.UserWebClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced integration test for UserController using @WebMvcTest.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Use @MockitoBean to mock the service layer, replacing the deprecated
    // @MockBean.
    @MockitoBean
    private UserWebClientService userWebClientService;

    @Test
    void shouldReturnAllUsers() throws Exception {
        // Arrange: mock the service method
        List<User> mockUsers = List.of(
                new User(1L, "Test", "test", "test@test.com"));
        when(userWebClientService.getAllUsers()).thenReturn(mockUsers);

        // Act & Assert: perform the GET request and verify the JSON response
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].name").value("Test"));
    }

    @Test
    void shouldReturnUserById() throws Exception {
        // Arrange: mock the service method
        User mockUser = new User(1L, "Test", "test", "test@test.com");
        when(userWebClientService.getUserById(1L)).thenReturn(mockUser);

        // Act & Assert: perform the GET request and verify the JSON response
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test"));
    }
}
