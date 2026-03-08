package com.example.simplefilter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple controller to test the filter.
 */
@RestController
public class HelloController {

    /**
     * A simple endpoint that returns a greeting.
     * The RequestLoggingFilter should intercept requests to this endpoint.
     * 
     * @return a simple string response
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello World!";
    }
}
