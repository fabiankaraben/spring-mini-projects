package com.example.validationgroups.service;

import com.example.validationgroups.domain.User;
import com.example.validationgroups.dto.UserRequest;
import com.example.validationgroups.exception.EmailAlreadyExistsException;
import com.example.validationgroups.exception.PasswordMismatchException;
import com.example.validationgroups.exception.UserNotFoundException;
import com.example.validationgroups.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for user management operations.
 *
 * <p>This class contains the business logic that sits between the REST controllers
 * and the database repository.  By the time any method here is called, the request
 * DTO has already been validated by the appropriate validation group in the controller,
 * so this layer can safely assume the input data respects the active group's constraints.</p>
 *
 * <h2>Validation groups recap (what this service receives)</h2>
 * <ul>
 *   <li>{@code create()} – receives a DTO validated with {@code OnCreate}: name, email,
 *       password, and role are all guaranteed non-null and non-blank.</li>
 *   <li>{@code update()} – receives a DTO validated with {@code OnUpdate}: only name and
 *       email are validated; password and role fields may be {@code null}.</li>
 *   <li>{@code changePassword()} – receives a DTO validated with {@code OnPasswordChange}:
 *       only newPassword and confirmPassword are guaranteed non-null.</li>
 * </ul>
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    /**
     * Constructor injection – preferred over field injection for testability.
     *
     * @param userRepository the JPA repository for user persistence
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns all users persisted in the database.
     *
     * @return a list of all users, possibly empty
     */
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Finds a single user by their ID.
     *
     * @param id the user's primary key
     * @return the found user entity
     * @throws UserNotFoundException if no user with that ID exists
     */
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Searches for users whose names contain the given keyword (case-insensitive).
     *
     * @param name the keyword to search within user names
     * @return a list of matching users, possibly empty
     */
    @Transactional(readOnly = true)
    public List<User> searchByName(String name) {
        return userRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Returns all users with the specified role.
     *
     * @param role the role to filter by (e.g. "ADMIN", "USER")
     * @return a list of users with that role, possibly empty
     */
    @Transactional(readOnly = true)
    public List<User> findByRole(String role) {
        return userRepository.findByRole(role);
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Creates a new user from the given request DTO.
     *
     * <p>At this point the DTO has been validated with {@code OnCreate}, so name,
     * email, password, and role are all guaranteed to satisfy their constraints.</p>
     *
     * @param request the validated create request
     * @return the newly persisted user entity
     * @throws EmailAlreadyExistsException if the email is already registered
     */
    public User create(UserRequest request) {
        // Guard: reject duplicate email addresses
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        // Build a new entity from the DTO fields
        User user = new User(
                request.name(),
                request.email(),
                request.password(),   // In production: encode with BCrypt
                request.role()
        );

        return userRepository.save(user);
    }

    /**
     * Updates the editable fields of an existing user.
     *
     * <p>At this point the DTO has been validated with {@code OnUpdate}, so only name
     * and email are guaranteed valid.  Password and role are intentionally ignored here –
     * they cannot be changed through this endpoint.</p>
     *
     * @param id      the ID of the user to update
     * @param request the validated update request (only name and email are used)
     * @return the updated user entity
     * @throws UserNotFoundException       if no user with that ID exists
     * @throws EmailAlreadyExistsException if the new email is already taken by another account
     */
    public User update(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        // Guard: if the email is being changed, check that the new email is not taken
        if (!user.getEmail().equalsIgnoreCase(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        // Only name and email can be changed through this endpoint.
        // Password and role are intentionally NOT updated here –
        // this is the business rule enforced by the OnUpdate validation group:
        // those fields are not even validated, reinforcing that they should not
        // be modified through the standard update flow.
        user.setName(request.name());
        user.setEmail(request.email());

        return userRepository.save(user);
    }

    /**
     * Changes the password of an existing user.
     *
     * <p>At this point the DTO has been validated with {@code OnPasswordChange}, so
     * newPassword and confirmPassword are guaranteed non-blank and newPassword has at
     * least 8 characters.  This method additionally checks that both fields match.</p>
     *
     * @param id      the ID of the user whose password should be changed
     * @param request the validated change-password request
     * @throws UserNotFoundException   if no user with that ID exists
     * @throws PasswordMismatchException if newPassword and confirmPassword differ
     */
    public void changePassword(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        // Cross-field validation: newPassword must equal confirmPassword.
        // This check cannot be expressed as a single-field Bean Validation constraint
        // without a custom annotation, so it is enforced here in the service layer.
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new PasswordMismatchException();
        }

        // In production, encode the password with BCrypt before storing it
        user.setPassword(request.newPassword());
        userRepository.save(user);
    }

    /**
     * Deletes a user by their ID.
     *
     * @param id the ID of the user to delete
     * @throws UserNotFoundException if no user with that ID exists
     */
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        userRepository.deleteById(user.getId());
    }
}
