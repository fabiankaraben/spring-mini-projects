package com.example.scheduledtask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class.
 * {@link EnableScheduling} ensures that a background task executor is created.
 * Without it, {@code @Scheduled} annotations will not be processed.
 */
@SpringBootApplication
@EnableScheduling
public class ScheduledTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduledTaskApplication.class, args);
    }

}
