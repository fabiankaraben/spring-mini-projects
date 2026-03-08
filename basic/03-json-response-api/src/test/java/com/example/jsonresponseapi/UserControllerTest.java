package com.example.jsonresponseapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// Imports for Mockito and MockMvc builders
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sliced Integration Test for UserController using @WebMvcTest.
 *
 * @WebMvcTest applies subset of Spring Boot configuration specifically
 *             related to web and MVC components, such
 *             as @Controller, @ControllerAdvice,
 *             and JSON converters. Real service dependencies are manually
 *             mocked using @MockitoBean.
 */
@WebMvcTest(UserController.class)
public class UserControllerTest {

    /**
     * MockMvc enables us to test HTTP requests to our controller code without
     * needing to actually start a real webserver inside our test environment.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * @MockitoBean registers a Mockito mock of the UserService within the Spring
     *              application
     *              context. When our Controller asks for a UserService, Spring will
     *              provide this mock.
     */
    @MockitoBean
    private UserService userService;

    /**
     * Here we test that making a GET request to /api/users/current relies on our
     * Mock,
     * returns a 200 OK HTTP status, responds with JSON, and accurately includes the
     * mocked data fields.
     */
    @Test
    public void testGetCurrentUser() throws Exception {
        // Arrange: Define behavior of the mock
        User mockUser = new User(99L, "Test Driven User", "test@test.com", "USER");
        when(userService.getCurrentUser()).thenReturn(mockUser);

        // Act & Assert: Execute the GET request and evaluate the outcome
        mockMvc.perform(get("/api/users/current"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // jsonPath evaluates parts of a JSON document. Here we verify the fields match
                // the mock.
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.name").value("Test Driven User"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }
}
