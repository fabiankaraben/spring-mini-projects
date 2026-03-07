package com.example.customresponsebodyadvice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo controller to demonstrate the Custom ResponseBodyAdvice functionality.
 * This controller provides endpoints that return Map responses, which will be
 * intercepted and modified by the CustomResponseBodyAdvice.
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    /**
     * GET endpoint that returns a simple demo response.
     * The response will be intercepted by CustomResponseBodyAdvice and
     * have a "processedAt" timestamp added.
     *
     * @return a Map containing demo data
     */
    @GetMapping("/data")
    public Map<String, Object> getDemoData() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is a demo response from the Custom ResponseBodyAdvice mini-project");
        response.put("status", "success");
        response.put("data", Map.of("key1", "value1", "key2", 42));

        return response;
    }

    /**
     * GET endpoint that returns user information.
     * This demonstrates how the advice applies to different responses.
     *
     * @return a Map containing user data
     */
    @GetMapping("/user")
    public Map<String, Object> getUserInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("userId", 12345);
        response.put("username", "demoUser");
        response.put("email", "demo@example.com");

        return response;
    }
}
