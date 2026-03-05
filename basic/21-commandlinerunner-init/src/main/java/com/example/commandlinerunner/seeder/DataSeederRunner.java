package com.example.commandlinerunner.seeder;

import com.example.commandlinerunner.model.User;
import com.example.commandlinerunner.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Spring Boot CommandLineRunner that seeds memory with initial dummy data.
 * The run() method will execute right after application context is loaded
 * and before the application starts accepting web requests.
 */
@Component
public class DataSeederRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeederRunner.class);

    private final UserRepository userRepository;

    public DataSeederRunner(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("CommandLineRunner is executing to seed the database...");

        // Prevent seeding data if it already exists (useful if we used an actual DB)
        if (userRepository.findAll().isEmpty()) {
            userRepository.save(new User(null, "Alice Smith", "alice@example.com"));
            userRepository.save(new User(null, "Bob Jones", "bob@example.com"));
            userRepository.save(new User(null, "Charlie Brown", "charlie@example.com"));

            logger.info("Database successfully seeded with {} entries.", userRepository.findAll().size());
        } else {
            logger.info("Database is already populated. Seeding skipped.");
        }

        // Log the commands arguments just to show how they are accessed
        logger.info("Application started with command-line arguments: {}", String.join(", ", args));
    }
}
