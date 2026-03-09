package com.example.rolebasedaccess.security;

import com.example.rolebasedaccess.domain.User;
import com.example.rolebasedaccess.repository.UserRepository;
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
 * <h2>Role in the authentication and authorisation flow</h2>
 * <p>This service is called in two distinct places:</p>
 * <ol>
 *   <li><strong>Login</strong> – the {@code AuthenticationManager} (configured
 *       in {@code SecurityConfig}) invokes the {@code DaoAuthenticationProvider},
 *       which calls {@link #loadUserByUsername} to fetch stored credentials and
 *       compares the submitted password against the stored BCrypt hash.</li>
 *   <li><strong>JWT validation on subsequent requests</strong> – the
 *       {@code JwtAuthenticationFilter} calls {@link #loadUserByUsername} after
 *       extracting the username from a valid JWT, so it can create an
 *       {@code Authentication} object with the correct {@code GrantedAuthority}
 *       list. This list is what {@code @PreAuthorize} checks against.</li>
 * </ol>
 *
 * <h2>Authority mapping</h2>
 * <p>The {@link com.example.rolebasedaccess.domain.Role} enum value (e.g.
 * {@code ROLE_ADMIN}) is stored as its string name in the database and converted
 * to a {@link SimpleGrantedAuthority}. Spring Security's {@code hasRole("ADMIN")}
 * expression automatically strips the {@code "ROLE_"} prefix when comparing,
 * so {@code ROLE_ADMIN} matches {@code hasRole("ADMIN")} correctly.</p>
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
     * converting the domain {@link com.example.rolebasedaccess.domain.Role} to a
     * {@link SimpleGrantedAuthority} that Spring Security can interpret.</p>
     *
     * <p>The authority string (e.g. {@code "ROLE_ADMIN"}) is the exact value
     * that {@code @PreAuthorize} expressions are evaluated against.</p>
     *
     * @param username the username submitted by the client (or extracted from the JWT)
     * @return a populated {@link UserDetails} instance ready for authentication
     * @throws UsernameNotFoundException if no user with that username exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Look up the user in the database; throw if not found.
        // UsernameNotFoundException is caught by DaoAuthenticationProvider and
        // converted to BadCredentialsException to avoid leaking whether a username exists.
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username));

        // Convert the domain Role enum to a GrantedAuthority.
        // The enum name (e.g. "ROLE_ADMIN") becomes the authority string.
        // Spring Security's hasRole("ADMIN") strips "ROLE_" automatically.
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().name());

        // Build and return the Spring Security UserDetails.
        // Using the built-in User record from spring-security-core.
        // Parameters: username, encodedPassword, list of authorities.
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(authority)
        );
    }
}
