package com.example.methodlevelsecurity.service;

import com.example.methodlevelsecurity.domain.Role;
import com.example.methodlevelsecurity.domain.User;
import com.example.methodlevelsecurity.dto.RegisterRequest;
import com.example.methodlevelsecurity.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 *
 * <h2>Testing strategy</h2>
 * <p>These tests use Mockito to stub the real {@link UserRepository} and
 * {@link PasswordEncoder}. No Spring context or database is required.
 * Each test runs in milliseconds and verifies a single piece of business logic.</p>
 *
 * <h2>@Secured / @PreAuthorize not enforced here</h2>
 * <p>Method-level security annotations are enforced by Spring Security's AOP
 * proxy, which is absent when we instantiate {@link UserService} directly via
 * Mockito. This is intentional: unit tests verify <em>business logic</em> only.
 * The security enforcement is verified in the integration test class.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService unit tests")
class UserServiceTest {

    /** Mocked repository – no actual DB calls. */
    @Mock
    private UserRepository userRepository;

    /**
     * Mocked password encoder. Using a mock avoids the BCrypt CPU cost
     * (10 rounds) in every test, keeping the suite fast.
     */
    @Mock
    private PasswordEncoder passwordEncoder;

    /** The system under test. Mockito injects the mocks via constructor. */
    @InjectMocks
    private UserService userService;

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private RegisterRequest req(String username, String password, String role) {
        RegisterRequest r = new RegisterRequest();
        r.setUsername(username);
        r.setPassword(password);
        r.setRole(role);
        return r;
    }

    // ── registerUser ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerUser: encodes password and defaults to ROLE_USER")
    void registerUser_encodesPasswordAndDefaultsToRoleUser() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("plain123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser(req("alice", "plain123", null));

        assertEquals("alice", result.getUsername());
        assertEquals("$2a$encoded", result.getPassword(),
                "Password must be BCrypt-encoded, not plain-text");
        assertEquals(Role.ROLE_USER, result.getRole(),
                "Role should default to ROLE_USER when not specified");

        verify(passwordEncoder).encode("plain123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("registerUser: assigns ROLE_ADMIN when requested")
    void registerUser_assignsAdminRoleWhenRequested() {
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser(req("admin", "pass", "ROLE_ADMIN"));

        assertEquals(Role.ROLE_ADMIN, result.getRole());
    }

    @Test
    @DisplayName("registerUser: assigns ROLE_MODERATOR when requested")
    void registerUser_assignsModeratorRole() {
        when(userRepository.existsByUsername("mod")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser(req("mod", "pass", "ROLE_MODERATOR"));

        assertEquals(Role.ROLE_MODERATOR, result.getRole());
    }

    @Test
    @DisplayName("registerUser: falls back to ROLE_USER for unknown role string")
    void registerUser_fallsBackForUnknownRole() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser(req("bob", "pass", "ROLE_SUPERUSER"));

        assertEquals(Role.ROLE_USER, result.getRole(),
                "Unrecognised role string should fall back to ROLE_USER");
    }

    @Test
    @DisplayName("registerUser: throws IllegalArgumentException when username is taken")
    void registerUser_throwsWhenUsernameTaken() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser(req("alice", "pass", null)));

        assertTrue(ex.getMessage().contains("alice"),
                "Error message should mention the duplicate username");
        verify(userRepository, never()).save(any());
    }

    // ── getAllUsers ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllUsers: returns all users from repository")
    void getAllUsers_returnsAllUsers() {
        List<User> stored = List.of(
                new User("alice", "$2a$1", Role.ROLE_USER),
                new User("admin", "$2a$2", Role.ROLE_ADMIN)
        );
        when(userRepository.findAll()).thenReturn(stored);

        List<User> result = userService.getAllUsers();

        assertEquals(2, result.size());
        verify(userRepository).findAll();
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserById: returns user when found")
    void getUserById_returnsUserWhenFound() {
        User user = new User("carol", "$2a$hash", Role.ROLE_USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.getUserById(1L);

        assertEquals("carol", result.getUsername());
    }

    @Test
    @DisplayName("getUserById: throws IllegalArgumentException when not found")
    void getUserById_throwsWhenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.getUserById(99L));
    }

    // ── updateUserRole ────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateUserRole: changes the user's role and saves")
    void updateUserRole_changesRoleAndSaves() {
        User user = new User("dave", "$2a$hash", Role.ROLE_USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateUserRole(1L, "ROLE_MODERATOR");

        assertEquals(Role.ROLE_MODERATOR, result.getRole());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateUserRole: throws for invalid role string")
    void updateUserRole_throwsForInvalidRoleString() {
        User user = new User("eve", "$2a$hash", Role.ROLE_USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUserRole(1L, "ROLE_INVALID"));
    }

    @Test
    @DisplayName("updateUserRole: throws when user not found")
    void updateUserRole_throwsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUserRole(99L, "ROLE_ADMIN"));
    }

    // ── deleteUser ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteUser: calls deleteById when user exists")
    void deleteUser_callsDeleteById() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteUser: throws and never calls deleteById when not found")
    void deleteUser_throwsWhenUserNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUser(99L));
        verify(userRepository, never()).deleteById(anyLong());
    }
}
