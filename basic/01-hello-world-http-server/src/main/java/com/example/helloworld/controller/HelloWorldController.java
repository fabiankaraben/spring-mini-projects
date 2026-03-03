package com.example.helloworld.controller;

import com.example.helloworld.service.HelloWorldService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller class that defines the API endpoints.
 * Annotated with @RestController so it's ready for use by Spring MVC to handle
 * web requests.
 */
@RestController
@RequestMapping("/api")
public class HelloWorldController {

    private final HelloWorldService helloWorldService;

    /**
     * Constructor injection of the HelloWorldService.
     * 
     * @param helloWorldService The service that holds business logic.
     */
    public HelloWorldController(HelloWorldService helloWorldService) {
        this.helloWorldService = helloWorldService;
    }

    /**
     * Handles GET requests directed to '/api/hello'
     * 
     * @return the string message from the service.
     */
    @GetMapping("/hello")
    public String sayHello() {
        return helloWorldService.getGreetingMessage();
    }
}
