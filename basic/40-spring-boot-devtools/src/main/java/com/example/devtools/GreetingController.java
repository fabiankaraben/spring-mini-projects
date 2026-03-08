package com.example.devtools;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A simple REST controller to demonstrate DevTools capabilities.
 * 
 * Try changing the return value of the greeting() method and saving the file.
 * If DevTools is working correctly, the application will automatically restart,
 * and the new message will be available at http://localhost:8080/greet without
 * manually stopping and starting the server.
 */
@RestController
public class GreetingController {

    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    /**
     * Endpoint that returns a greeting message.
     * 
     * @return A string greeting.
     */
    @GetMapping("/greet")
    public String greeting() {
        // Change the string in GreetingService and save the file to see the auto-restart in action!
        return greetingService.getGreeting();
    }
}
