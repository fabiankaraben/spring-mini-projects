package com.example.demo.controller;

import com.example.demo.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link UserController}.
 * <p>
 * This test class verifies the logic of the controller methods in isolation.
 * Since the controller methods are simple, we just check that they return the expected objects.
 * Note that these tests do NOT involve the {@code GlobalResponseBodyAdvice}, so the return values
 * are the raw objects (User, String), not the wrapped responses.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UserControllerUnitTest {

    private final UserController userController = new UserController();

    @Test
    void getUserById_shouldReturnUser() {
        Long userId = 1L;
        User user = userController.getUserById(userId);

        assertEquals(userId, user.getId());
        assertEquals("John Doe", user.getName());
        assertEquals("john.doe@example.com", user.getEmail());
    }

    @Test
    void hello_shouldReturnString() {
        String response = userController.hello();
        assertEquals("Hello World", response);
    }
}
