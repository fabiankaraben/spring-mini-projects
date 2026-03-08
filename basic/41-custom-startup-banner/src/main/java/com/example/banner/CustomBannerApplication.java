package com.example.banner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Main entry point for the Custom Startup Banner application.
 * <p>
 * This application demonstrates how to customize the startup banner in a Spring Boot application.
 * By placing a file named 'banner.txt' in the 'src/main/resources' directory, Spring Boot
 * automatically detects it and displays its content in the console during startup, replacing
 * the default Spring Boot banner.
 * </p>
 */
@SpringBootApplication
public class CustomBannerApplication {

    public static void main(String[] args) {
        // Run the Spring Boot application.
        // This will trigger the banner printing process before the application fully starts.
        SpringApplication.run(CustomBannerApplication.class, args);
    }

}

/**
 * A simple REST controller to verify the application is running.
 * <p>
 * This controller handles HTTP GET requests to the root URL ("/").
 * It uses {@link BannerService} to retrieve the welcome message.
 * </p>
 */
@RestController
class BannerController {

    private final BannerService bannerService;

    /**
     * Constructor injection for the BannerService.
     * 
     * @param bannerService The service to retrieve the welcome message from.
     */
    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }
    
    /**
     * Handles the root request.
     * 
     * @return A welcome message indicating the application started successfully.
     */
    @GetMapping("/")
    public String home() {
        return bannerService.getWelcomeMessage();
    }
}
