package com.example.jwtgeneration.security;

import com.example.jwtgeneration.domain.User;
import com.example.jwtgeneration.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of Spring Security's {@link UserDetailsService} that loads
 * user information from the PostgreSQL database via {@link UserRepository}.
 *
 * <h2>Role in the authentication flow</h2>
 * When a client POSTs credentials to {@code /api/auth/login}, the
 * {@code AuthenticationManager} (configured in {@code SecurityConfig}) invokes
 * the {@code DaoAuthenticationProvider}, which:
 * <ol>
 *   <li>Calls {@link #loadUserByUsername} to fetch the stored {@link UserDetails}.</li>
 *   <li>Compares the submitted password against the stored BCrypt hash using
 *       the configured {@code PasswordEncoder}.</li>
 *   <li>Throws {@code BadCredentialsException} if they do not match, or returns
 *       a fully authenticated {@code Authentication} object if they do.</li>
 * </ol>
 *
 * <p>This class is kept deliberately thin – it is just a bridge between the
 * Spring Security contract and our JPA repository.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads a {@link UserDetails} object by username.
     *
     * <p>The returned object wraps our {@link User} entity in Spring Security's
     * own {@link org.springframework.security.core.userdetails.User} record,
     * converting the domain {@link com.example.jwtgeneration.domain.Role} to a
     * {@link SimpleGrantedAuthority} that Spring Security can interpret.
     *
     * @param username the username submitted by the client
     * @return a populated {@link UserDetails} instance ready for authentication
     * @throws UsernameNotFoundException if no user with that username exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Look up the user in the database; throw if not found.
        // UsernameNotFoundException is a Spring Security type that the
        // DaoAuthenticationProvider catches and converts to BadCredentialsException
        // (to avoid leaking whether a username exists).
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username));

        // Convert the domain Role enum to a GrantedAuthority.
        // SimpleGrantedAuthority simply wraps the role name string (e.g. "ROLE_USER").
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().name());

        // Build and return the Spring Security UserDetails object.
        // We use the built-in User record from spring-security-core.
        // Parameters: username, encodedPassword, list of authorities.
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(authority)
        );
    }
}
