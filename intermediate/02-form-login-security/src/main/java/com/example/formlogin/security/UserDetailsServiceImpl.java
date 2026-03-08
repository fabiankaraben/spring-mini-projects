package com.example.formlogin.security;

import com.example.formlogin.entity.User;
import com.example.formlogin.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom implementation of Spring Security's {@link UserDetailsService}.
 *
 * <p>Spring Security calls {@link #loadUserByUsername(String)} during the
 * authentication phase of form login. This implementation fetches the user
 * from the database and converts it into a {@link UserDetails} object that
 * the security framework can understand.
 *
 * <p>The password stored in the database is a BCrypt hash; Spring Security
 * will compare the hash with the plain-text password entered on the login form
 * using the {@link org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder}
 * configured in {@link com.example.formlogin.config.SecurityConfig}.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads user details by username.
     *
     * <p>This method is called by Spring Security automatically during form login
     * when the user submits the login form. The returned {@link UserDetails} object
     * contains the username, encoded password, and granted authorities (roles).
     *
     * @param username the username submitted on the login form
     * @return a fully populated {@link UserDetails} object
     * @throws UsernameNotFoundException if no user with the given username exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Look up the user in the database; throw a descriptive exception if not found
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username));

        // Build a Spring Security UserDetails from our domain entity.
        // .roles() automatically prepends "ROLE_" to the role string, so "USER"
        // becomes the authority "ROLE_USER".
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }
}
