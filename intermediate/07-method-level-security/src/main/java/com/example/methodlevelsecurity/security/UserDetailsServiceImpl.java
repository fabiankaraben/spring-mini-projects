package com.example.methodlevelsecurity.security;

import com.example.methodlevelsecurity.domain.User;
import com.example.methodlevelsecurity.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of Spring Security's {@link UserDetailsService} that loads
 * user details from the PostgreSQL database via {@link UserRepository}.
 *
 * <p>Spring Security calls {@link #loadUserByUsername(String)} during:</p>
 * <ol>
 *   <li>Login: to retrieve the user and verify the submitted password against the
 *       stored BCrypt hash using the configured {@link org.springframework.security.crypto.password.PasswordEncoder}.</li>
 *   <li>JWT filter: to confirm the user referenced in the JWT still exists in the
 *       database (prevents deleted users from using stale tokens).</li>
 * </ol>
 *
 * <p>The {@link org.springframework.security.core.GrantedAuthority} list returned
 * contains exactly one authority: the user's role string (e.g. {@code "ROLE_ADMIN"}).
 * This is what {@code @PreAuthorize("hasRole('ADMIN')")} evaluates against.</p>
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads a {@link UserDetails} object for the given username.
     *
     * <p>The returned {@link UserDetails} wraps the {@link User} entity's
     * credentials and role as a single {@link SimpleGrantedAuthority}.</p>
     *
     * @param username the username to look up
     * @return a Spring Security {@link UserDetails} object
     * @throws UsernameNotFoundException if no user with the given username exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Load the User entity from PostgreSQL; throw if not found
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username));

        // Wrap the single Role enum value as a SimpleGrantedAuthority.
        // The authority string (e.g. "ROLE_ADMIN") is what @PreAuthorize evaluates.
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
}
