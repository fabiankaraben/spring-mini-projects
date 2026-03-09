package com.example.rolebasedaccess.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link User} domain entity.
 *
 * <p>These are pure unit tests – no Spring context, no database, no mocks.
 * They verify the domain object's construction, getters/setters, and
 * the {@code toString()} contract (password must never appear in the output).</p>
 *
 * <h2>Why test domain objects?</h2>
 * <p>Domain entities contain business rules (e.g. role assignment). Testing
 * them in isolation is the fastest and most focused way to prove correctness
 * before integration concerns are introduced.</p>
 */
@DisplayName("User domain entity")
class UserTest {

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("should store username, password and role when constructed")
    void constructor_storesAllFields() {
        // Arrange & Act
        User user = new User("alice", "encodedPassword123", Role.ROLE_USER);

        // Assert each field is accessible via the getter
        assertEquals("alice",              user.getUsername());
        assertEquals("encodedPassword123", user.getPassword());
        assertEquals(Role.ROLE_USER,        user.getRole());
    }

    @Test
    @DisplayName("should have null id before persistence")
    void id_isNullBeforePersistence() {
        // The id is only assigned by the database; a freshly constructed entity has null.
        User user = new User("bob", "hash", Role.ROLE_ADMIN);
        assertNull(user.getId(),
                "id should be null before the entity is saved to the database");
    }

    // ── Role assignment ───────────────────────────────────────────────────────

    @Test
    @DisplayName("should accept ROLE_USER role")
    void role_acceptsRoleUser() {
        User user = new User("carol", "hash", Role.ROLE_USER);
        assertEquals(Role.ROLE_USER, user.getRole());
    }

    @Test
    @DisplayName("should accept ROLE_MODERATOR role")
    void role_acceptsRoleModerator() {
        User user = new User("dave", "hash", Role.ROLE_MODERATOR);
        assertEquals(Role.ROLE_MODERATOR, user.getRole());
    }

    @Test
    @DisplayName("should accept ROLE_ADMIN role")
    void role_acceptsRoleAdmin() {
        User user = new User("eve", "hash", Role.ROLE_ADMIN);
        assertEquals(Role.ROLE_ADMIN, user.getRole());
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should update username via setter")
    void setUsername_updatesValue() {
        User user = new User("original", "hash", Role.ROLE_USER);
        user.setUsername("updated");
        assertEquals("updated", user.getUsername());
    }

    @Test
    @DisplayName("should update role via setter")
    void setRole_updatesRole() {
        User user = new User("frank", "hash", Role.ROLE_USER);
        // Simulate a role promotion
        user.setRole(Role.ROLE_MODERATOR);
        assertEquals(Role.ROLE_MODERATOR, user.getRole());
    }

    @Test
    @DisplayName("should update password via setter")
    void setPassword_updatesPassword() {
        User user = new User("grace", "oldHash", Role.ROLE_USER);
        user.setPassword("newHash");
        assertEquals("newHash", user.getPassword());
    }

    // ── toString safety ───────────────────────────────────────────────────────

    @Test
    @DisplayName("toString should NOT expose the password hash")
    void toString_doesNotExposePassword() {
        // This is a security requirement: the password hash must never appear
        // in log output or debug strings.
        User user = new User("henry", "superSecretHash", Role.ROLE_ADMIN);
        String output = user.toString();

        assertFalse(output.contains("superSecretHash"),
                "toString() must not include the password hash to prevent accidental log exposure");
    }

    @Test
    @DisplayName("toString should include the username")
    void toString_includesUsername() {
        User user = new User("ivan", "hash", Role.ROLE_USER);
        assertTrue(user.toString().contains("ivan"));
    }

    @Test
    @DisplayName("toString should include the role")
    void toString_includesRole() {
        User user = new User("judy", "hash", Role.ROLE_ADMIN);
        assertTrue(user.toString().contains("ROLE_ADMIN"));
    }
}
