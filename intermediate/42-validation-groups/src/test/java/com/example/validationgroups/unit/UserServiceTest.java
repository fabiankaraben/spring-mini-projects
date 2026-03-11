package com.example.validationgroups.unit;

import com.example.validationgroups.domain.User;
import com.example.validationgroups.dto.UserRequest;
import com.example.validationgroups.exception.EmailAlreadyExistsException;
import com.example.validationgroups.exception.PasswordMismatchException;
import com.example.validationgroups.exception.UserNotFoundException;
import com.example.validationgroups.repository.UserRepository;
import com.example.validationgroups.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 *
 * <h2>Testing approach</h2>
 * <p>These tests exercise the <em>service layer logic</em> in isolation.  The
 * {@link UserRepository} is mocked via Mockito so no database or Spring context
 * is started.  This makes the tests extremely fast and focused on business rules.</p>
 *
 * <h2>What is tested here</h2>
 * <ul>
 *   <li>CRUD operations: create, findAll, findById, update, delete.</li>
 *   <li>Business rules: duplicate email rejection, password change logic,
 *       password mismatch detection.</li>
 *   <li>Error conditions: {@link UserNotFoundException} for missing IDs,
 *       {@link EmailAlreadyExistsException} for duplicate emails,
 *       {@link PasswordMismatchException} for mismatched passwords.</li>
 * </ul>
 *
 * <h2>Why no validation groups here?</h2>
 * <p>Validation groups are enforced by Spring's {@code @Validated} on the controller
 * methods – <em>before</em> the service is called.  By the time a service method
 * executes, the DTO is already validated.  These unit tests therefore focus on what
 * happens with already-valid (or intentionally bad) data, not on the validation itself.
 * The integration tests in {@link com.example.validationgroups.integration.ValidationGroupsIntegrationTest}
 * verify that the correct group is applied end-to-end.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService – Unit Tests")
class UserServiceTest {

    /**
     * Mocked repository – no real database is created; all calls are intercepted
     * by Mockito and return whatever we configure in each test.
     */
    @Mock
    private UserRepository userRepository;

    /**
     * The class under test. {@code @InjectMocks} creates an instance and injects
     * the mocked {@link UserRepository} via constructor injection.
     */
    @InjectMocks
    private UserService userService;

    /** A reusable user instance created before each test. */
    private User sampleUser;

    @BeforeEach
    void setUp() {
        // Build a sample user that simulates one already persisted in the database
        sampleUser = new User("Alice Smith", "alice@example.com", "secret123", "USER");
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll() should return all users from repository")
    void findAll_shouldReturnAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));

        List<User> result = userService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alice Smith");
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll() should return empty list when no users exist")
    void findAll_shouldReturnEmptyList_whenNoUsers() {
        when(userRepository.findAll()).thenReturn(List.of());

        List<User> result = userService.findAll();

        assertThat(result).isEmpty();
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById() should return user when found")
    void findById_shouldReturnUser_whenFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        User result = userService.findById(1L);

        assertThat(result.getName()).isEqualTo("Alice Smith");
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("findById() should throw UserNotFoundException when user does not exist")
    void findById_shouldThrow_whenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create() should save and return new user")
    void create_shouldSaveAndReturnUser() {
        // Simulate request after OnCreate validation: name, email, password, role present
        UserRequest request = new UserRequest(
                "Bob Jones", "bob@example.com", "password123", "USER", null, null);

        // No duplicate email
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(
                new User("Bob Jones", "bob@example.com", "password123", "USER"));

        User result = userService.create(request);

        assertThat(result.getName()).isEqualTo("Bob Jones");
        assertThat(result.getEmail()).isEqualTo("bob@example.com");
        assertThat(result.getRole()).isEqualTo("USER");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("create() should throw EmailAlreadyExistsException when email is already registered")
    void create_shouldThrow_whenEmailAlreadyExists() {
        UserRequest request = new UserRequest(
                "Duplicate User", "alice@example.com", "password123", "USER", null, null);

        // Simulate that alice@example.com is already taken
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("alice@example.com");

        // Verify save was never called – the guard blocked it
        verify(userRepository, never()).save(any());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update() should change name and email only (password and role are ignored)")
    void update_shouldChangeNameAndEmailOnly() {
        // Simulate request after OnUpdate validation: only name and email are validated
        UserRequest request = new UserRequest(
                "Alice Updated", "alice.updated@example.com", null, null, null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByEmail("alice.updated@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        userService.update(1L, request);

        // Only name and email should change; password and role are untouched
        assertThat(sampleUser.getName()).isEqualTo("Alice Updated");
        assertThat(sampleUser.getEmail()).isEqualTo("alice.updated@example.com");
        // Original password is unchanged because update() does NOT modify it
        assertThat(sampleUser.getPassword()).isEqualTo("secret123");
        // Original role is unchanged
        assertThat(sampleUser.getRole()).isEqualTo("USER");
        verify(userRepository).save(sampleUser);
    }

    @Test
    @DisplayName("update() should throw UserNotFoundException when user does not exist")
    void update_shouldThrow_whenUserNotFound() {
        UserRequest request = new UserRequest(
                "Name", "email@example.com", null, null, null, null);

        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(42L, request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("42");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() should throw EmailAlreadyExistsException when new email is already taken")
    void update_shouldThrow_whenNewEmailIsAlreadyTaken() {
        // Trying to change alice's email to one that belongs to another account
        UserRequest request = new UserRequest(
                "Alice Smith", "taken@example.com", null, null, null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("taken@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() should allow keeping the same email (no conflict with own email)")
    void update_shouldAllowKeepingSameEmail() {
        // Request uses the same email as the current user – should not throw
        UserRequest request = new UserRequest(
                "Alice Updated Name", "alice@example.com", null, null, null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        userService.update(1L, request);

        // existsByEmail must NOT be called because the email did not change
        verify(userRepository, never()).existsByEmail(anyString());
        assertThat(sampleUser.getName()).isEqualTo("Alice Updated Name");
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    @DisplayName("changePassword() should update the password when newPassword matches confirmPassword")
    void changePassword_shouldUpdatePassword_whenPasswordsMatch() {
        // Request after OnPasswordChange validation: newPassword and confirmPassword present
        UserRequest request = new UserRequest(
                null, null, null, null, "newPassword123", "newPassword123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        userService.changePassword(1L, request);

        assertThat(sampleUser.getPassword()).isEqualTo("newPassword123");
        verify(userRepository).save(sampleUser);
    }

    @Test
    @DisplayName("changePassword() should throw PasswordMismatchException when passwords do not match")
    void changePassword_shouldThrow_whenPasswordsDoNotMatch() {
        // newPassword and confirmPassword are different – service must reject this
        UserRequest request = new UserRequest(
                null, null, null, null, "newPassword123", "differentPassword");

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> userService.changePassword(1L, request))
                .isInstanceOf(PasswordMismatchException.class)
                .hasMessageContaining("do not match");

        // Password must NOT have been changed
        assertThat(sampleUser.getPassword()).isEqualTo("secret123");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword() should throw UserNotFoundException when user does not exist")
    void changePassword_shouldThrow_whenUserNotFound() {
        UserRequest request = new UserRequest(
                null, null, null, null, "newPassword123", "newPassword123");

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(99L, request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");

        verify(userRepository, never()).save(any());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete() should call repository deleteById for existing user")
    void delete_shouldCallDeleteById() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        userService.delete(1L);

        verify(userRepository).deleteById(any());
    }

    @Test
    @DisplayName("delete() should throw UserNotFoundException for non-existent user")
    void delete_shouldThrow_whenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");

        verify(userRepository, never()).deleteById(any());
    }

    // ── searchByName ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("searchByName() should return matching users from repository")
    void searchByName_shouldReturnMatchingUsers() {
        when(userRepository.findByNameContainingIgnoreCase("alice"))
                .thenReturn(List.of(sampleUser));

        List<User> result = userService.searchByName("alice");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alice Smith");
    }

    // ── findByRole ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByRole() should return users with the specified role")
    void findByRole_shouldReturnUsersWithRole() {
        when(userRepository.findByRole("USER")).thenReturn(List.of(sampleUser));

        List<User> result = userService.findByRole("USER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo("USER");
    }

    // ── Domain model: UserResponse.from() ─────────────────────────────────────

    @Test
    @DisplayName("UserResponse.from() should map all fields correctly and exclude password")
    void userResponse_shouldMapCorrectly() {
        var response = com.example.validationgroups.dto.UserResponse.from(sampleUser);

        assertThat(response.name()).isEqualTo("Alice Smith");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.active()).isTrue();
        // Password must NOT appear in the response DTO
        // (no password field in UserResponse)
    }
}
