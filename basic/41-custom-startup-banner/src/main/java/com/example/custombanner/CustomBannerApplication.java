package com.example.custombanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Custom Startup Banner Spring Boot application.
 * This application demonstrates how to display a custom ASCII art banner on startup.
 * The banner is defined in the banner.txt file located in src/main/resources.
 */
@SpringBootApplication
public class CustomBannerApplication {

    /**
     * The main method that starts the Spring Boot application.
     * When the application starts, Spring Boot automatically reads the banner.txt file
     * from the classpath and displays the custom ASCII art in the console.
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(CustomBannerApplication.class, args);
    }
}
