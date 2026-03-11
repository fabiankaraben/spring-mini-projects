package com.example.ratelimitingfilter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Sample REST controller that exposes two endpoints to demonstrate the
 * rate-limiting interceptor in action.
 *
 * <p>All requests to {@code /api/**} are intercepted by
 * {@link com.example.ratelimitingfilter.interceptor.RateLimitInterceptor}
 * before they reach these handler methods. If the client has exceeded the
 * allowed request rate the interceptor returns HTTP 429 directly and these
 * methods are never invoked.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    /**
     * Simple "ping" endpoint.
     *
     * <p>Use this to verify that the application is running and to observe
     * the rate-limiting headers returned in the response.
     *
     * @return a JSON object with a greeting and the current server timestamp
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "message", "pong",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * A heavier "data" endpoint to simulate a real business resource.
     *
     * <p>Both this endpoint and {@link #ping()} share the same per-client
     * token bucket, so calling either one consumes a token.
     *
     * @return a JSON object representing a sample data payload
     */
    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> data() {
        return ResponseEntity.ok(Map.of(
                "resource", "sample-data",
                "value", 42,
                "timestamp", Instant.now().toString()
        ));
    }
}
