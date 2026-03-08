package com.example.hateoas.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test for Greeting domain object.
 */
public class GreetingTest {

    @Test
    public void testGreetingContent() {
        Greeting greeting = new Greeting("Hello, Test!");
        assertEquals("Hello, Test!", greeting.getContent());
    }
}
