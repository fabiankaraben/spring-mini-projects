package com.example.formlogin.security;

import com.example.formlogin.entity.User;
import com.example.formlogin.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserDetailsServiceImpl}.
 *
 * <p>These tests are pure unit tests: they do NOT start a Spring context and
 * do NOT require a database. Mockito is used to stub the
 * {@link UserRepository} so that the tests run in milliseconds and are
 * fully isolated from infrastructure concerns.
 *
 * <p>{@link ExtendWith(MockitoExtension.class)} activates Mockito's JUnit 5
 * integration, which automatically creates mocks for {@code @Mock} fields
 * and injects them into the {@code @InjectMocks} target.
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    /** Stubbed repository – no real database is involved. */
    @Mock
    private UserRepository userRepository;

    /** The class under test, with the mock repository injected by Mockito. */
    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    // ─────────────────────────────────────────────────────────────────────────
    // loadUserByUsername – happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadUserByUsername: existing regular user → returns correct UserDetails")
    void loadUserByUsername_ExistingUser_ReturnsUserDetails() {
        // Arrange: prepare a User entity that the repository will return
        String username = "testuser";
        User user = new User(username, "encodedPassword123", "USER");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // Act: call the method under test
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Assert: the returned UserDetails must reflect the entity data
        assertNotNull(userDetails, "UserDetails should not be null");
        assertEquals(username, userDetails.getUsername(), "Username must match");
        assertEquals("encodedPassword123", userDetails.getPassword(), "Password hash must match");

        // Spring Security's .roles("USER") automatically adds the "ROLE_" prefix,
        // so the authority string stored is "ROLE_USER", not "USER".
        boolean hasRoleUser = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_USER"::equals);
        assertTrue(hasRoleUser, "User should have ROLE_USER authority");

        // Verify the repository was queried exactly once
        verify(userRepository, times(1)).findByUsername(username);
    }

    @Test
    @DisplayName("loadUserByUsername: existing admin user → returns ROLE_ADMIN authority")
    void loadUserByUsername_ExistingAdminUser_ReturnsAdminAuthority() {
        // Arrange
        String username = "adminuser";
        User admin = new User(username, "adminHashedPwd", "ADMIN");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));

        // Act
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Assert: admin role should translate to ROLE_ADMIN
        boolean hasRoleAdmin = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        assertTrue(hasRoleAdmin, "Admin should have ROLE_ADMIN authority");

        // A user with a single role should have exactly one authority
        assertEquals(1, userDetails.getAuthorities().size(), "Should have exactly one authority");

        verify(userRepository, times(1)).findByUsername(username);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadUserByUsername – failure path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadUserByUsername: non-existent user → throws UsernameNotFoundException")
    void loadUserByUsername_UserDoesNotExist_ThrowsUsernameNotFoundException() {
        // Arrange: repository returns empty to simulate a missing user
        String username = "ghost";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act & Assert: the service must throw UsernameNotFoundException
        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(username),
                "Should throw UsernameNotFoundException for missing user"
        );

        // The exception message should contain the missing username so that
        // the error is easy to diagnose in logs.
        assertTrue(exception.getMessage().contains(username),
                "Exception message should contain the username");

        verify(userRepository, times(1)).findByUsername(username);
    }

    @Test
    @DisplayName("loadUserByUsername: repository called with exact username")
    void loadUserByUsername_CallsRepositoryWithExactUsername() {
        // Arrange
        String username = "exactMatch";
        User user = new User(username, "pwd", "USER");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // Act
        userDetailsService.loadUserByUsername(username);

        // Assert: verify the repository was called with the same string, not a
        // trimmed or lowercased version – the service must not alter the username.
        verify(userRepository).findByUsername(username);
        verifyNoMoreInteractions(userRepository);
    }
}
