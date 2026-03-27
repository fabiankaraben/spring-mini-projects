package com.example.keycloakidentity.service;

import com.example.keycloakidentity.domain.User;
import com.example.keycloakidentity.dto.CreateUserRequest;
import com.example.keycloakidentity.dto.UpdateUserRequest;
import com.example.keycloakidentity.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService}.
 *
 * <p>These tests verify the service's business logic in isolation from the repository
 * and from Spring. We mock {@link UserRepository} to return controlled data and verify
 * that the service delegates correctly and maps DTOs to domain objects.
 *
 * <p><b>Testing approach:</b>
 * <ul>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} activates Mockito without loading
 *       a Spring context — tests start in milliseconds.</li>
 *   <li>The repository is mocked with {@code @Mock} — no real in-memory store is involved,
 *       giving complete control over what the repository returns.</li>
 *   <li>Nested test classes group test methods by service method under test.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — unit tests")
class UserServiceTest {

    /**
     * Mocked repository — Mockito injects this automatically via @ExtendWith.
     */
    @Mock
    private UserRepository userRepository;

    /** The service under test, constructed with the mocked repository. */
    private UserService userService;

    /** A reusable sample user returned by mocked repository calls. */
    private User sampleUser;

    @BeforeEach
    void setUp() {
        // Construct service manually (not via Spring) — makes the test fast and focused
        userService = new UserService(userRepository);

        // Build a sample user for reuse across tests
        sampleUser = new User(
                1L,
                "kcid-user-0001",
                "Alice Admin",
                "alice@example.com",
                "ADMIN",
                true,
                Instant.now(),
                Instant.now()
        );
    }

    // =========================================================================
    // getAllUsers()
    // =========================================================================

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsersTests {

        /**
         * Verifies that getAllUsers() delegates to the repository and returns its result.
         */
        @Test
        @DisplayName("returns all users from repository")
        void returnsAllUsersFromRepository() {
            when(userRepository.findAll()).thenReturn(List.of(sampleUser));

            List<User> result = userService.getAllUsers();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("alice@example.com");
        }

        /**
         * Verifies that an empty repository returns an empty list (not null).
         */
        @Test
        @DisplayName("returns empty list when repository is empty")
        void returnsEmptyListWhenRepositoryEmpty() {
            when(userRepository.findAll()).thenReturn(List.of());

            List<User> result = userService.getAllUsers();

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // getUserById()
    // =========================================================================

    @Nested
    @DisplayName("getUserById()")
    class GetUserByIdTests {

        /**
         * Verifies that getUserById() returns the user wrapped in Optional.present when found.
         */
        @Test
        @DisplayName("returns Optional.present when user exists")
        void returnsUserWhenFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            Optional<User> result = userService.getUserById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
        }

        /**
         * Verifies that getUserById() returns Optional.empty when the user does not exist.
         */
        @Test
        @DisplayName("returns Optional.empty when user does not exist")
        void returnsEmptyWhenNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            Optional<User> result = userService.getUserById(99L);

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // getUserByKeycloakId()
    // =========================================================================

    @Nested
    @DisplayName("getUserByKeycloakId()")
    class GetUserByKeycloakIdTests {

        /**
         * Verifies that getUserByKeycloakId() finds the user linked to a Keycloak UUID.
         * This is the key integration point used by the /api/users/me endpoint.
         */
        @Test
        @DisplayName("returns user when Keycloak UUID matches")
        void returnsUserWhenKeycloakIdMatches() {
            when(userRepository.findByKeycloakId("kcid-user-0001"))
                    .thenReturn(Optional.of(sampleUser));

            Optional<User> result = userService.getUserByKeycloakId("kcid-user-0001");

            assertThat(result).isPresent();
            assertThat(result.get().getKeycloakId()).isEqualTo("kcid-user-0001");
        }

        /**
         * Verifies that getUserByKeycloakId() returns empty when no user is linked to the UUID.
         * This happens when a Keycloak user has no application profile yet.
         */
        @Test
        @DisplayName("returns Optional.empty when no user linked to Keycloak UUID")
        void returnsEmptyWhenKeycloakIdNotFound() {
            when(userRepository.findByKeycloakId("unknown-keycloak-id"))
                    .thenReturn(Optional.empty());

            Optional<User> result = userService.getUserByKeycloakId("unknown-keycloak-id");

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // getUsersByRole()
    // =========================================================================

    @Nested
    @DisplayName("getUsersByRole()")
    class GetUsersByRoleTests {

        /**
         * Verifies that getUsersByRole() delegates to repository and returns matching users.
         */
        @Test
        @DisplayName("returns users matching the given role")
        void returnsUsersMatchingRole() {
            when(userRepository.findByRole("ADMIN")).thenReturn(List.of(sampleUser));

            List<User> result = userService.getUsersByRole("ADMIN");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRole()).isEqualTo("ADMIN");
        }

        /**
         * Verifies that an unknown role returns an empty list.
         */
        @Test
        @DisplayName("returns empty list for unknown role")
        void returnsEmptyListForUnknownRole() {
            when(userRepository.findByRole("SUPERUSER")).thenReturn(List.of());

            List<User> result = userService.getUsersByRole("SUPERUSER");

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // createUser()
    // =========================================================================

    @Nested
    @DisplayName("createUser()")
    class CreateUserTests {

        /**
         * Verifies that createUser() correctly maps the CreateUserRequest DTO to a User
         * domain object and delegates to the repository.
         */
        @Test
        @DisplayName("maps CreateUserRequest to User and saves it")
        void mapsRequestAndSavesUser() {
            CreateUserRequest request = new CreateUserRequest();
            request.setDisplayName("Bob User");
            request.setEmail("bob@example.com");
            request.setRole("USER");
            request.setKeycloakId("kcid-new-0005");

            User savedUser = new User(
                    5L, "kcid-new-0005", "Bob User", "bob@example.com",
                    "USER", true, Instant.now(), Instant.now()
            );
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = userService.createUser(request);

            // Verify the saved user has the expected values
            assertThat(result.getId()).isEqualTo(5L);
            assertThat(result.getDisplayName()).isEqualTo("Bob User");
            assertThat(result.getEmail()).isEqualTo("bob@example.com");
            assertThat(result.getRole()).isEqualTo("USER");
            assertThat(result.getKeycloakId()).isEqualTo("kcid-new-0005");
            assertThat(result.isActive()).isTrue();

            // Verify the repository was called with a User domain object
            verify(userRepository).save(any(User.class));
        }

        /**
         * Verifies that new users are always created as active (the active=true default).
         */
        @Test
        @DisplayName("creates user with active=true by default")
        void createsUserAsActiveByDefault() {
            CreateUserRequest request = new CreateUserRequest();
            request.setDisplayName("Carol Viewer");
            request.setEmail("carol@example.com");
            request.setRole("USER");

            // Capture what the service passes to the repository
            User savedUser = new User(
                    6L, null, "Carol Viewer", "carol@example.com",
                    "USER", true, Instant.now(), Instant.now()
            );
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = userService.createUser(request);

            // The service should default active to true
            assertThat(result.isActive()).isTrue();
        }

        /**
         * Verifies that the keycloakId can be null (user not yet linked to Keycloak).
         */
        @Test
        @DisplayName("allows null keycloakId (user not yet linked to Keycloak)")
        void allowsNullKeycloakId() {
            CreateUserRequest request = new CreateUserRequest();
            request.setDisplayName("Dave Pending");
            request.setEmail("dave@example.com");
            request.setRole("USER");
            // keycloakId is null — not set

            User savedUser = new User(
                    7L, null, "Dave Pending", "dave@example.com",
                    "USER", true, Instant.now(), Instant.now()
            );
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = userService.createUser(request);

            assertThat(result.getKeycloakId()).isNull();
        }
    }

    // =========================================================================
    // updateUser()
    // =========================================================================

    @Nested
    @DisplayName("updateUser()")
    class UpdateUserTests {

        /**
         * Verifies that updateUser() applies only non-null fields (partial update semantics).
         */
        @Test
        @DisplayName("applies only non-null fields (partial update)")
        void appliesOnlyNonNullFields() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.update(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Update request with only the displayName changed
            UpdateUserRequest request = new UpdateUserRequest();
            request.setDisplayName("Alice Super Admin");
            // email, role, active are null → should not change

            Optional<User> result = userService.updateUser(1L, request);

            assertThat(result).isPresent();
            assertThat(result.get().getDisplayName()).isEqualTo("Alice Super Admin");
            assertThat(result.get().getEmail()).isEqualTo("alice@example.com"); // unchanged
            assertThat(result.get().getRole()).isEqualTo("ADMIN"); // unchanged
            assertThat(result.get().isActive()).isTrue(); // unchanged
        }

        /**
         * Verifies that all non-null update fields are applied when a full update is provided.
         */
        @Test
        @DisplayName("applies all non-null fields when full update is provided")
        void appliesAllNonNullFields() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.update(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateUserRequest request = new UpdateUserRequest();
            request.setDisplayName("Alice Updated");
            request.setEmail("alice.updated@example.com");
            request.setRole("USER");
            request.setActive(false);

            Optional<User> result = userService.updateUser(1L, request);

            assertThat(result).isPresent();
            assertThat(result.get().getDisplayName()).isEqualTo("Alice Updated");
            assertThat(result.get().getEmail()).isEqualTo("alice.updated@example.com");
            assertThat(result.get().getRole()).isEqualTo("USER");
            assertThat(result.get().isActive()).isFalse();
        }

        /**
         * Verifies that updateUser() returns Optional.empty when the user does not exist.
         */
        @Test
        @DisplayName("returns Optional.empty when user does not exist")
        void returnsEmptyWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            UpdateUserRequest request = new UpdateUserRequest();
            request.setDisplayName("Ghost User");

            Optional<User> result = userService.updateUser(99L, request);

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // deleteUser()
    // =========================================================================

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUserTests {

        /**
         * Verifies that deleteUser() returns true when the user was successfully deleted.
         */
        @Test
        @DisplayName("returns true when user is deleted successfully")
        void returnsTrueWhenDeleted() {
            when(userRepository.deleteById(1L)).thenReturn(true);

            boolean result = userService.deleteUser(1L);

            assertThat(result).isTrue();
        }

        /**
         * Verifies that deleteUser() returns false when no user with the given ID exists.
         */
        @Test
        @DisplayName("returns false when user does not exist")
        void returnsFalseWhenNotFound() {
            when(userRepository.deleteById(99L)).thenReturn(false);

            boolean result = userService.deleteUser(99L);

            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // getUserCount()
    // =========================================================================

    @Test
    @DisplayName("getUserCount() delegates to repository.count()")
    void getUserCountDelegatesToRepository() {
        when(userRepository.count()).thenReturn(4);

        int count = userService.getUserCount();

        assertThat(count).isEqualTo(4);
    }
}
