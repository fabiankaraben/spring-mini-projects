package com.example.commandlinerunner.seeder;

import com.example.commandlinerunner.model.User;
import com.example.commandlinerunner.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test to verify the CommandLineRunner implementation.
 */
@ExtendWith(MockitoExtension.class)
public class DataSeederRunnerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DataSeederRunner dataSeederRunner;

    @Test
    public void testRun_WhenRepositoryIsEmpty_ThenSeedData() throws Exception {
        // Arrange
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        dataSeederRunner.run("test-arg");

        // Assert
        // We verify that the save method is called exactly 3 times
        verify(userRepository, times(3)).save(any(User.class));
    }

    @Test
    public void testRun_WhenRepositoryIsNotEmpty_ThenSkipSeeding() throws Exception {
        // Arrange
        // Simulate that the database already has users
        when(userRepository.findAll()).thenReturn(List.of(new User("1", "Existing User", "exist@example.com")));

        // Act
        dataSeederRunner.run("test-arg");

        // Assert
        // We verify that save was never called since data already exists
        verify(userRepository, never()).save(any(User.class));
    }
}
