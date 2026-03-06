package com.example.mockmvctesting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A simple REST controller demonstrating basic endpoints.
 * This controller will be tested using @WebMvcTest in the test class.
 */
@RestController
public class HelloController {

    /**
     * Endpoint that returns a greeting message.
     * Accessible via GET /hello
     *
     * @return A simple greeting string
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }

    /**
     * Endpoint that returns a personalized greeting.
     * Accessible via GET /hello/{name}
     *
     * @param name The name to greet
     * @return A personalized greeting string
     */
    @GetMapping("/hello/{name}")
    public String helloName(@PathVariable String name) {
        return "Hello, " + name + "!";
    }
}
