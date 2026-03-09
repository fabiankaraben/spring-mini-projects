package com.example.oauth2loginclient.service;

import com.example.oauth2loginclient.domain.AppUser;
import com.example.oauth2loginclient.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AppUserService}.
 *
 * <p>These tests run entirely in-memory using Mockito to stub the
 * {@link AppUserRepository}. No Spring context, no database, and no Docker
 * container are started – making this test class fast and self-contained.</p>
 *
 * <p>Mockito is bootstrapped via the {@link MockitoExtension} JUnit 5 extension
 * instead of {@code @SpringBootTest}, which keeps startup time near zero.</p>
 */
@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    /** Mocked repository – all calls are stubbed, nothing hits a real DB. */
    @Mock
    private AppUserRepository userRepository;

    /**
     * The system under test. {@code @InjectMocks} creates an instance of
     * {@link AppUserService} and injects the mocked repository via the
     * constructor.
     */
    @InjectMocks
    private AppUserService appUserService;

    /** A reusable AppUser fixture for tests that need an existing user. */
    private AppUser existingUser;

    @BeforeEach
    void setUp() {
        // Create a pre-populated AppUser to simulate an already-persisted record.
        // We manually set fields that are normally set by @PrePersist or the DB.
        existingUser = new AppUser("github", "12345", "Alice", "alice@example.com",
                "https://avatars.githubusercontent.com/u/12345");
    }

    // ── upsertUser – INSERT path ─────────────────────────────────────────────

    @Test
    @DisplayName("upsertUser: creates a new user when no existing record is found")
    void upsertUser_shouldCreateNewUser_whenUserDoesNotExist() {
        // Arrange: repository returns empty (user has never logged in before)
        when(userRepository.findByProviderAndProviderId("github", "99999"))
                .thenReturn(Optional.empty());
        // repository.save() returns whatever entity is passed to it
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        AppUser result = appUserService.upsertUser(
                "github", "99999", "Bob", "bob@example.com", "https://avatar.url/bob");

        // Assert: the returned user has the expected values
        assertThat(result.getProvider()).isEqualTo("github");
        assertThat(result.getProviderId()).isEqualTo("99999");
        assertThat(result.getName()).isEqualTo("Bob");
        assertThat(result.getEmail()).isEqualTo("bob@example.com");
        assertThat(result.getAvatarUrl()).isEqualTo("https://avatar.url/bob");

        // Verify that save was called exactly once
        verify(userRepository, times(1)).save(any(AppUser.class));
    }

    @Test
    @DisplayName("upsertUser: persists the entity via repository.save on INSERT path")
    void upsertUser_shouldCallSaveOnce_whenCreatingNewUser() {
        // Arrange
        when(userRepository.findByProviderAndProviderId(any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        appUserService.upsertUser("google", "sub-abc", "Carol", "carol@gmail.com", null);

        // Use an ArgumentCaptor to capture what was passed to save()
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());

        // The captured entity should have the correct provider
        assertThat(captor.getValue().getProvider()).isEqualTo("google");
        assertThat(captor.getValue().getProviderId()).isEqualTo("sub-abc");
    }

    // ── upsertUser – UPDATE path ─────────────────────────────────────────────

    @Test
    @DisplayName("upsertUser: updates mutable fields when user already exists")
    void upsertUser_shouldUpdateExistingUser_whenUserAlreadyExists() {
        // Arrange: repository returns the existing user
        when(userRepository.findByProviderAndProviderId("github", "12345"))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act: call upsert with updated profile data
        AppUser result = appUserService.upsertUser(
                "github", "12345", "Alice Updated", "alice-new@example.com",
                "https://avatars.githubusercontent.com/u/12345?v=2");

        // Assert: the mutable fields are updated
        assertThat(result.getName()).isEqualTo("Alice Updated");
        assertThat(result.getEmail()).isEqualTo("alice-new@example.com");
        assertThat(result.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/12345?v=2");

        // The provider/providerId are immutable – they should not have changed
        assertThat(result.getProvider()).isEqualTo("github");
        assertThat(result.getProviderId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("upsertUser: refreshes lastLoginAt on UPDATE path")
    void upsertUser_shouldRefreshLastLoginAt_whenUserAlreadyExists() {
        // Arrange: record the time before calling upsert
        Instant before = Instant.now();

        when(userRepository.findByProviderAndProviderId("github", "12345"))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        AppUser result = appUserService.upsertUser(
                "github", "12345", "Alice", "alice@example.com", "https://avatar.url");

        Instant after = Instant.now();

        // Assert: lastLoginAt was updated to a timestamp within the test window
        assertThat(result.getLastLoginAt()).isNotNull();
        assertThat(result.getLastLoginAt()).isAfterOrEqualTo(before);
        assertThat(result.getLastLoginAt()).isBeforeOrEqualTo(after);
    }

    // ── findAllUsers ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAllUsers: delegates to repository and returns all users")
    void findAllUsers_shouldReturnAllUsers() {
        // Arrange
        AppUser user2 = new AppUser("google", "sub-xyz", "Dave", "dave@gmail.com", null);
        when(userRepository.findAll()).thenReturn(List.of(existingUser, user2));

        // Act
        List<AppUser> result = appUserService.findAllUsers();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(existingUser, user2);
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAllUsers: returns empty list when no users exist")
    void findAllUsers_shouldReturnEmptyList_whenNoUsersExist() {
        // Arrange
        when(userRepository.findAll()).thenReturn(List.of());

        // Act
        List<AppUser> result = appUserService.findAllUsers();

        // Assert
        assertThat(result).isEmpty();
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById: returns present Optional when user exists")
    void findById_shouldReturnUser_whenUserExists() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        // Act
        Optional<AppUser> result = appUserService.findById(1L);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(existingUser);
    }

    @Test
    @DisplayName("findById: returns empty Optional when user does not exist")
    void findById_shouldReturnEmpty_whenUserNotFound() {
        // Arrange
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        Optional<AppUser> result = appUserService.findById(999L);

        // Assert
        assertThat(result).isEmpty();
    }

    // ── findByProviderAndProviderId ──────────────────────────────────────────

    @Test
    @DisplayName("findByProviderAndProviderId: returns user when matching record exists")
    void findByProviderAndProviderId_shouldReturnUser_whenFound() {
        // Arrange
        when(userRepository.findByProviderAndProviderId("github", "12345"))
                .thenReturn(Optional.of(existingUser));

        // Act
        Optional<AppUser> result = appUserService.findByProviderAndProviderId("github", "12345");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getProviderId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("findByProviderAndProviderId: returns empty Optional when not found")
    void findByProviderAndProviderId_shouldReturnEmpty_whenNotFound() {
        // Arrange
        when(userRepository.findByProviderAndProviderId("github", "unknown"))
                .thenReturn(Optional.empty());

        // Act
        Optional<AppUser> result = appUserService.findByProviderAndProviderId("github", "unknown");

        // Assert
        assertThat(result).isEmpty();
    }
}
