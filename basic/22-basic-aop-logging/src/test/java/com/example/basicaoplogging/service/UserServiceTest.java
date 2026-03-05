package com.example.basicaoplogging.service;

import com.example.basicaoplogging.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateUser() {
        User user = userService.createUser("Alice");

        assertThat(user).isNotNull();
        assertThat(user.name()).isEqualTo("Alice");
    }

    @Test
    void testGetAllUsers() {
        userService.createUser("Alice");
        userService.createUser("Bob");

        List<User> users = userService.getAllUsers();

        assertThat(users).hasSize(2);
    }

    @Test
    void testGetUserByIdFound() {
        User user = userService.createUser("Alice");

        Optional<User> found = userService.getUserById(user.id());

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Alice");
    }

    @Test
    void testGetUserByIdNotFound() {
        Optional<User> found = userService.getUserById(99L);

        assertThat(found).isNotPresent();
    }

    @Test
    void testDeleteUser() {
        User user = userService.createUser("Alice");

        boolean isDeleted = userService.deleteUser(user.id());

        assertThat(isDeleted).isTrue();
        assertThat(userService.getAllUsers()).isEmpty();
    }

    @Test
    void testThrowExceptionMethod() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.throwExceptionMethod("Test Error");
        });

        assertThat(exception.getMessage()).contains("Test Error");
    }
}
