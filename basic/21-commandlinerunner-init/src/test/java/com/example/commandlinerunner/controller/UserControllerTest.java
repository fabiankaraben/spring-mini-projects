package com.example.commandlinerunner.controller;

import com.example.commandlinerunner.model.User;
import com.example.commandlinerunner.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced integration test for the UserController.
 */
@WebMvcTest(UserController.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Use @MockitoBean as per new rules (instead of @MockBean)
    @MockitoBean
    private UserRepository userRepository;

    @Test
    public void testGetAllUsers() throws Exception {
        // Arrange
        List<User> mockUsers = List.of(
                new User("1", "John Doe", "john@example.com"),
                new User("2", "Jane Doe", "jane@example.com"));
        when(userRepository.findAll()).thenReturn(mockUsers);

        // Act & Assert
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[1].name").value("Jane Doe"));
    }

    @Test
    public void testGetUserById_whenExists() throws Exception {
        // Arrange
        User user = new User("1", "John Doe", "john@example.com");
        when(userRepository.findById("1")).thenReturn(Optional.of(user));

        // Act & Assert
        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    public void testGetUserById_whenDoesNotExist() throws Exception {
        // Arrange
        when(userRepository.findById("99")).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound());
    }
}
