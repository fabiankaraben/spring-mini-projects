package com.example.jwtgeneration.unit;

import com.example.jwtgeneration.domain.Role;
import com.example.jwtgeneration.domain.User;
import com.example.jwtgeneration.dto.RegisterRequest;
import com.example.jwtgeneration.repository.UserRepository;
import com.example.jwtgeneration.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 *
 * <p>All external dependencies ({@link UserRepository} and {@link PasswordEncoder})
 * are replaced with Mockito mocks so that these tests:
 * <ul>
 *   <li>Run instantly – no database or Spring context startup.</li>
 *   <li>Test only the logic inside {@link UserService} in isolation.</li>
 *   <li>Are deterministic – mock return values are fully controlled.</li>
 * </ul>
 *
 * <h2>Mockito annotations used</h2>
 * <ul>
 *   <li>{@code @Mock} – creates a mock object for the annotated field.</li>
 *   <li>{@code @InjectMocks} – instantiates {@link UserService} and injects
 *       the mocks into its constructor automatically.</li>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} – activates Mockito
 *       annotation processing for JUnit 5.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService – Unit Tests")
class UserServiceTest {

    /** Mock replacing the real JPA repository – no DB connection needed. */
    @Mock
    private UserRepository userRepository;

    /**
     * Mock replacing BCrypt – we control what "encoding" returns so tests
     * are not slowed down by real BCrypt computation.
     */
    @Mock
    private PasswordEncoder passwordEncoder;

    /** The class under test, with mocks injected via constructor. */
    @InjectMocks
    private UserService userService;

    /** Reusable register request fixture. */
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("alice", "password123");
    }

    // ── registerUser – happy path ─────────────────────────────────────────────

    @Test
    @DisplayName("registerUser should save and return the new user when username is available")
    void registerUser_shouldSaveAndReturnUser_whenUsernameIsAvailable() {
        // Arrange: username does not exist yet
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        // Simulate BCrypt encoding (we just prefix "encoded_" for clarity)
        when(passwordEncoder.encode("password123")).thenReturn("encoded_password123");
        // Simulate the repository returning the persisted user with an ID
        User savedUser = new User("alice", "encoded_password123", Role.ROLE_USER);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        User result = userService.registerUser(registerRequest);

        // Assert: the returned user has the expected username and ROLE_USER role
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getRole()).isEqualTo(Role.ROLE_USER);
        // The stored password must be the encoded version, never plain text
        assertThat(result.getPassword()).isEqualTo("encoded_password123");

        // Verify interactions: encode was called once, save was called once
        verify(passwordEncoder, times(1)).encode("password123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("registerUser should assign ROLE_USER by default")
    void registerUser_shouldAssignRoleUserByDefault() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.registerUser(registerRequest);

        // New self-registered users must always receive ROLE_USER, not ROLE_ADMIN
        assertThat(result.getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    @DisplayName("registerUser should encode the password before saving")
    void registerUser_shouldEncodePasswordBeforeSaving() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$bcrypt_hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.registerUser(registerRequest);

        // The password stored in the entity must be the encoded value
        assertThat(result.getPassword()).isEqualTo("$2a$10$bcrypt_hash");
        // Plain-text password must never be stored
        assertThat(result.getPassword()).doesNotContain("password123");
    }

    // ── registerUser – duplicate username ─────────────────────────────────────

    @Test
    @DisplayName("registerUser should throw IllegalArgumentException when username already exists")
    void registerUser_shouldThrowException_whenUsernameAlreadyExists() {
        // Arrange: username is already taken
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        // Act & Assert: expect an exception with a meaningful message
        assertThatThrownBy(() -> userService.registerUser(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alice")
                .hasMessageContaining("already taken");

        // Verify that save was never called – no partial write should occur
        verify(userRepository, never()).save(any(User.class));
        // Verify that encode was never called – no wasted CPU on BCrypt
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("registerUser should check existence before encoding to avoid wasted work")
    void registerUser_shouldCheckExistenceBeforeEncoding() {
        // Arrange: duplicate username
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        // Act: ignore the expected exception
        try {
            userService.registerUser(registerRequest);
        } catch (IllegalArgumentException ignored) {}

        // Assert: existsByUsername was called once, passwordEncoder was never called
        verify(userRepository, times(1)).existsByUsername("alice");
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ── registerUser – repository interaction ─────────────────────────────────

    @Test
    @DisplayName("registerUser should call existsByUsername exactly once with the correct username")
    void registerUser_shouldCallExistsByUsernameOnce() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.registerUser(registerRequest);

        // The existence check must use the exact username from the request
        verify(userRepository, times(1)).existsByUsername("alice");
    }
}
