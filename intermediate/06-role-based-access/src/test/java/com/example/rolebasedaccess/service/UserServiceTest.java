package com.example.rolebasedaccess.service;

import com.example.rolebasedaccess.domain.Role;
import com.example.rolebasedaccess.domain.User;
import com.example.rolebasedaccess.dto.RegisterRequest;
import com.example.rolebasedaccess.repository.UserRepository;
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
 * <p>These tests use Mockito to replace the real {@link UserRepository} and
 * {@link PasswordEncoder} with test doubles (mocks). This means:</p>
 * <ul>
 *   <li>No Spring context is started – tests run in milliseconds.</li>
 *   <li>No database is required – all data is simulated by mock behaviour.</li>
 *   <li>The service logic is tested in pure isolation.</li>
 * </ul>
 *
 * <h2>@PreAuthorize note</h2>
 * <p>{@code @PreAuthorize} annotations are enforced by Spring Security's AOP
 * proxy. Since we instantiate {@link UserService} directly with Mockito (not via
 * Spring), the proxy is <strong>not</strong> present here. This is intentional:
 * unit tests verify the <em>business logic</em> of each method. The security
 * enforcement is tested separately in the integration test class.</p>
 *
 * <h2>@ExtendWith(MockitoExtension.class)</h2>
 * <p>This JUnit 5 extension activates Mockito annotation processing
 * ({@code @Mock}, {@code @InjectMocks}) without needing a Spring context.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService unit tests")
class UserServiceTest {

    /** Mocked repository – no actual DB calls are made. */
    @Mock
    private UserRepository userRepository;

    /**
     * Mocked password encoder. Using a mock instead of a real BCryptPasswordEncoder
     * avoids the BCrypt CPU cost (10 rounds) in every test.
     */
    @Mock
    private PasswordEncoder passwordEncoder;

    /**
     * The system under test. Mockito injects the mocks above into its constructor.
     */
    @InjectMocks
    private UserService userService;

    // ── Fixture helpers ───────────────────────────────────────────────────────

    /**
     * Creates a {@link RegisterRequest} DTO for use in tests.
     */
    private RegisterRequest registerRequest(String username, String password, String role) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setRole(role);
        return req;
    }

    // ── registerUser ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerUser: should encode password and save user with ROLE_USER by default")
    void registerUser_encodesPasswordAndSavesWithDefaultRole() {
        // Arrange
        RegisterRequest request = registerRequest("alice", "plain123", null);

        // The username does NOT exist yet
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        // The encoder returns a fake hash
        when(passwordEncoder.encode("plain123")).thenReturn("$2a$encoded");
        // The repository returns the saved entity
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        User result = userService.registerUser(request);

        // Assert
        assertEquals("alice",      result.getUsername());
        assertEquals("$2a$encoded", result.getPassword(), "Password must be encoded, not plain");
        assertEquals(Role.ROLE_USER, result.getRole(),    "Default role must be ROLE_USER");

        // Verify the encoder was actually called
        verify(passwordEncoder).encode("plain123");
        // Verify the repository save was called
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("registerUser: should assign ROLE_ADMIN when 'ROLE_ADMIN' is requested")
    void registerUser_assignsAdminRoleWhenRequested() {
        // Arrange
        RegisterRequest request = registerRequest("admin", "adminpass", "ROLE_ADMIN");

        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        User result = userService.registerUser(request);

        // Assert
        assertEquals(Role.ROLE_ADMIN, result.getRole());
    }

    @Test
    @DisplayName("registerUser: should assign ROLE_MODERATOR when 'ROLE_MODERATOR' is requested")
    void registerUser_assignsModeratorRole() {
        RegisterRequest request = registerRequest("mod", "modpass", "ROLE_MODERATOR");

        when(userRepository.existsByUsername("mod")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser(request);

        assertEquals(Role.ROLE_MODERATOR, result.getRole());
    }

    @Test
    @DisplayName("registerUser: should fall back to ROLE_USER for unknown role string")
    void registerUser_fallsBackToRoleUserForUnknownRole() {
        RegisterRequest request = registerRequest("bob", "pass", "ROLE_SUPERUSER");

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser(request);

        assertEquals(Role.ROLE_USER, result.getRole(),
                "An unrecognised role string should default to ROLE_USER");
    }

    @Test
    @DisplayName("registerUser: should throw IllegalArgumentException when username is already taken")
    void registerUser_throwsWhenUsernameTaken() {
        // Arrange: the username already exists
        RegisterRequest request = registerRequest("existing", "pass", null);
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser(request)
        );

        assertTrue(ex.getMessage().contains("existing"),
                "Error message should mention the duplicate username");

        // The repository save must NOT be called when the username is taken
        verify(userRepository, never()).save(any());
    }

    // ── getAllUsers ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllUsers: should return all users from the repository")
    void getAllUsers_returnsAllUsers() {
        // Arrange
        List<User> stored = List.of(
                new User("alice", "$2a$1", Role.ROLE_USER),
                new User("admin", "$2a$2", Role.ROLE_ADMIN)
        );
        when(userRepository.findAll()).thenReturn(stored);

        // Act
        List<User> result = userService.getAllUsers();

        // Assert
        assertEquals(2, result.size());
        verify(userRepository).findAll();
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserById: should return user when found")
    void getUserById_returnsUserWhenFound() {
        // Arrange
        User user = new User("carol", "$2a$hash", Role.ROLE_USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Act
        User result = userService.getUserById(1L);

        // Assert
        assertEquals("carol", result.getUsername());
    }

    @Test
    @DisplayName("getUserById: should throw IllegalArgumentException when not found")
    void getUserById_throwsWhenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.getUserById(99L));
    }

    // ── updateUserRole ────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateUserRole: should change the user's role and save")
    void updateUserRole_changesRoleAndSaves() {
        // Arrange
        User user = new User("dave", "$2a$hash", Role.ROLE_USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        User result = userService.updateUserRole(1L, "ROLE_MODERATOR");

        // Assert
        assertEquals(Role.ROLE_MODERATOR, result.getRole());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateUserRole: should throw when the new role string is invalid")
    void updateUserRole_throwsForInvalidRoleString() {
        User user = new User("eve", "$2a$hash", Role.ROLE_USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUserRole(1L, "ROLE_INVALID"));
    }

    @Test
    @DisplayName("updateUserRole: should throw when user not found")
    void updateUserRole_throwsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUserRole(99L, "ROLE_ADMIN"));
    }

    // ── deleteUser ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteUser: should call repository deleteById when user exists")
    void deleteUser_callsDeleteById() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteUser: should throw when user not found")
    void deleteUser_throwsWhenUserNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUser(99L));

        // deleteById must NOT be called if the user doesn't exist
        verify(userRepository, never()).deleteById(anyLong());
    }
}
