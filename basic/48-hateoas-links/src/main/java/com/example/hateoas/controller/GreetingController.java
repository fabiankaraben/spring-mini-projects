package com.example.hateoas.controller;

import com.example.hateoas.domain.Greeting;
import com.example.hateoas.service.GreetingService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@RestController
public class GreetingController {

    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    /**
     * Endpoint to get a greeting.
     * It adds a self link and a link to the same method with a different parameter.
     *
     * @param name the name to greet
     * @return the greeting resource with links
     */
    @GetMapping("/greeting")
    public HttpEntity<Greeting> greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
        Greeting greeting = greetingService.greet(name);

        // Add self link
        greeting.add(linkTo(methodOn(GreetingController.class).greeting(name)).withSelfRel());

        // Add link to greet with "Spring" if the current name is not "Spring"
        if (!"Spring".equals(name)) {
            greeting.add(linkTo(methodOn(GreetingController.class).greeting("Spring")).withRel("greet-spring"));
        }

        return new ResponseEntity<>(greeting, HttpStatus.OK);
    }
}
