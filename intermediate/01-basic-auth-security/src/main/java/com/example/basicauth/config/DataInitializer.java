package com.example.basicauth.config;

import com.example.basicauth.entity.User;
import com.example.basicauth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Initializes the database with some sample users on application startup.
 */
@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Check if users already exist to avoid duplicates on restart
            if (userRepository.count() == 0) {
                // Create a standard user
                User user = new User("user", passwordEncoder.encode("password"), "USER");
                userRepository.save(user);

                // Create an admin user
                User admin = new User("admin", passwordEncoder.encode("admin123"), "ADMIN");
                userRepository.save(admin);
            }
        };
    }
}
