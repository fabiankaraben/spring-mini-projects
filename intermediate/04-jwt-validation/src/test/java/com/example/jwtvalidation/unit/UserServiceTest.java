package com.example.jwtvalidation.unit;

import com.example.jwtvalidation.domain.Role;
import com.example.jwtvalidation.domain.User;
import com.example.jwtvalidation.dto.RegisterRequest;
import com.example.jwtvalidation.repository.UserRepository;
import com.example.jwtvalidation.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link UserService}.
 *
 * <h2>Strategy</h2>
 * <p>We use {@link ExtendWith(MockitoExtension.class)} to automatically
 * create {@link Mock} instances for the dependencies ({@link UserRepository}
 * and {@link PasswordEncoder}). This lets us test the service logic in
 * complete isolation – no Spring context, no database, no network.</p>
 *
 * <h2>What is tested</h2>
 * <ul>
 *   <li>Happy path: a new user is registered with a BCrypt-encoded password
 *       and the {@code ROLE_USER} role.</li>
 *   <li>Duplicate username: an {@link IllegalArgumentException} is thrown
 *       when the requested username already exists.</li>
 *   <li>Side effects: the repository's {@code save()} is called exactly once
 *       on success and never on failure.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService – Unit Tests")
class UserServiceTest {

    /** Mock repository – no real database interaction. */
    @Mock
    private UserRepository userRepository;

    /** Mock encoder – we control what it returns without BCrypt computation. */
    @Mock
    private PasswordEncoder passwordEncoder;

    /** Service under test – constructed manually with the mock dependencies. */
    private UserService userService;

    @BeforeEach
    void setUp() {
        // Construct the service with mock dependencies rather than @InjectMocks
        // so the test is explicit about what is being passed.
        userService = new UserService(userRepository, passwordEncoder);
    }

    // ── registerUser – happy path ─────────────────────────────────────────────

    @Test
    @DisplayName("registerUser should save a new user with an encoded password and ROLE_USER")
    void registerUser_shouldSaveUserWithEncodedPasswordAndRoleUser() {
        // Arrange
        RegisterRequest request = new RegisterRequest("alice", "plainPassword");

        // Username does not exist yet
        given(userRepository.findByUsername("alice")).willReturn(Optional.empty());

        // Mock the encoder to return a predictable hash
        given(passwordEncoder.encode("plainPassword")).willReturn("$2a$10$hashedPassword");

        // Mock save to return the persisted user (simulates what the DB would return)
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            // Return the same User object that was passed to save()
            return invocation.getArgument(0, User.class);
        });

        // Act
        User savedUser = userService.registerUser(request);

        // Assert – the returned user has the correct attributes
        assertThat(savedUser.getUsername()).isEqualTo("alice");
        assertThat(savedUser.getPassword()).isEqualTo("$2a$10$hashedPassword");
        assertThat(savedUser.getRole()).isEqualTo(Role.ROLE_USER);

        // Verify that the encoder was called with the plain-text password
        verify(passwordEncoder).encode("plainPassword");

        // Verify that save was called exactly once
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("registerUser should call passwordEncoder.encode with the plain-text password")
    void registerUser_shouldEncodePasswordBeforeSaving() {
        // Arrange
        RegisterRequest request = new RegisterRequest("bob", "mySecret");
        given(userRepository.findByUsername("bob")).willReturn(Optional.empty());
        given(passwordEncoder.encode(eq("mySecret"))).willReturn("encodedSecret");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        userService.registerUser(request);

        // Assert – the plain-text password must be encoded before persisting
        verify(passwordEncoder).encode("mySecret");
    }

    @Test
    @DisplayName("registerUser should always assign ROLE_USER to a new user")
    void registerUser_shouldAlwaysAssignRoleUser() {
        // Arrange
        RegisterRequest request = new RegisterRequest("carol", "password123");
        given(userRepository.findByUsername("carol")).willReturn(Optional.empty());
        given(passwordEncoder.encode(any())).willReturn("hashed");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        User savedUser = userService.registerUser(request);

        // Assert – self-registered users always get ROLE_USER, never ROLE_ADMIN
        assertThat(savedUser.getRole()).isEqualTo(Role.ROLE_USER);
    }

    // ── registerUser – duplicate username ─────────────────────────────────────

    @Test
    @DisplayName("registerUser should throw IllegalArgumentException when username is already taken")
    void registerUser_shouldThrowIllegalArgumentException_whenUsernameAlreadyExists() {
        // Arrange – simulate an existing user with username "alice"
        RegisterRequest request = new RegisterRequest("alice", "anotherPassword");
        User existingUser = new User("alice", "$2a$10$alreadyHashedPass", Role.ROLE_USER);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(existingUser));

        // Act & Assert – expect an exception with a helpful message
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alice")
                .hasMessageContaining("already taken");
    }

    @Test
    @DisplayName("registerUser should NOT call save when the username is already taken")
    void registerUser_shouldNotCallSave_whenUsernameAlreadyExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest("alice", "pass");
        given(userRepository.findByUsername("alice"))
                .willReturn(Optional.of(new User("alice", "hash", Role.ROLE_USER)));

        // Act – ignore the exception (we only care about the side-effects here)
        try {
            userService.registerUser(request);
        } catch (IllegalArgumentException ignored) {
            // Expected
        }

        // Assert – save must never be called if the username check failed
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("registerUser should NOT call passwordEncoder when the username is already taken")
    void registerUser_shouldNotEncodePassword_whenUsernameAlreadyExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest("alice", "pass");
        given(userRepository.findByUsername("alice"))
                .willReturn(Optional.of(new User("alice", "hash", Role.ROLE_USER)));

        // Act
        try {
            userService.registerUser(request);
        } catch (IllegalArgumentException ignored) {
            // Expected
        }

        // Assert – if the username is taken, we must not waste CPU encoding a password
        verify(passwordEncoder, never()).encode(any());
    }
}
