package com.example.formlogin.config;

import com.example.formlogin.entity.User;
import com.example.formlogin.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Populates the database with demo users when the application starts.
 *
 * <p>The check {@code userRepository.count() == 0} ensures that we don't
 * create duplicate rows when the application is restarted against the same
 * database (e.g. via Docker Compose with a persistent volume).
 *
 * <p>In a real application you would use a migration tool such as Flyway or
 * Liquibase instead of a {@link CommandLineRunner}. Here we keep it simple for
 * educational purposes.
 */
@Configuration
public class DataInitializer {

    /**
     * Seeds the database with a regular user and an admin user on first run.
     *
     * <p>Passwords are encoded with BCrypt before being saved – never store
     * plain-text passwords in the database.
     *
     * <p>Demo credentials:
     * <ul>
     *   <li>username: {@code user} / password: {@code password} → role USER</li>
     *   <li>username: {@code admin} / password: {@code admin123} → role ADMIN</li>
     * </ul>
     *
     * @param userRepository the repository used to persist users
     * @param passwordEncoder the BCrypt encoder defined in {@link SecurityConfig}
     * @return a {@link CommandLineRunner} that runs once on startup
     */
    @Bean
    public CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Only seed if the table is empty to avoid duplicates on restart
            if (userRepository.count() == 0) {
                // Standard user: can access /dashboard and /profile
                User user = new User("user", passwordEncoder.encode("password"), "USER");
                userRepository.save(user);

                // Admin user: can access everything, including /admin/** endpoints
                User admin = new User("admin", passwordEncoder.encode("admin123"), "ADMIN");
                userRepository.save(admin);

                System.out.println("[DataInitializer] Created demo users: 'user' and 'admin'");
            }
        };
    }
}
