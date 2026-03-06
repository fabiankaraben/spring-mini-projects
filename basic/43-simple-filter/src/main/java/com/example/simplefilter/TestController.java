package com.example.simplefilter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A simple REST controller to demonstrate the filter functionality.
 * This controller provides an endpoint that can be used to test the LoggingFilter.
 */
@RestController
public class TestController {

    /**
     * A simple GET endpoint that returns a greeting message.
     * When accessed, it will trigger the LoggingFilter which logs the request
     * and adds a custom header to the response.
     * @return a greeting message
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello, World! This request was processed by the LoggingFilter.";
    }
}
