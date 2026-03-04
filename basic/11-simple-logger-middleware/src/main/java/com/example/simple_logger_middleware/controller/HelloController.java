package com.example.simple_logger_middleware.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A sample REST Controller to demonstrate the RequestLoggingInterceptor.
 */
@RestController
public class HelloController {

    /**
     * Endpoint that simply returns a greeting message.
     * The interceptor will log its request method, path, and duration.
     */
    @GetMapping("/hello")
    public String sayHello() throws InterruptedException {
        // Sleep to simulate some processing time
        Thread.sleep(100);
        return "Hello World!";
    }
}
