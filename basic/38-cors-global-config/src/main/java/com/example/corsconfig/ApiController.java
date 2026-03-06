package com.example.corsconfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Sample REST controller to demonstrate CORS-enabled API endpoints.
 * This controller provides basic CRUD-like operations that can be accessed
 * from different origins due to the global CORS configuration.
 *
 * Endpoints:
 * - GET /api/hello: Returns a simple greeting message
 * - POST /api/data: Accepts JSON data and echoes it back
 * - PUT /api/data/{id}: Simulates updating a resource by ID
 * - DELETE /api/data/{id}: Simulates deleting a resource by ID
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private GreetingService greetingService;

    /**
     * Handles GET requests to /api/hello.
     * Returns a greeting message to demonstrate CORS functionality.
     *
     * @return ResponseEntity with greeting message
     */
    @GetMapping("/hello")
    public ResponseEntity<String> getHello() {
        return ResponseEntity.ok(greetingService.getGreeting());
    }

    /**
     * Handles POST requests to /api/data.
     * Accepts JSON data in the request body and echoes it back.
     * This endpoint demonstrates POST method support in CORS.
     *
     * @param data the data sent in the request body
     * @return ResponseEntity echoing the received data
     */
    @PostMapping("/data")
    public ResponseEntity<String> createData(@RequestBody String data) {
        return ResponseEntity.ok("Received data: " + data);
    }

    /**
     * Handles PUT requests to /api/data/{id}.
     * Simulates updating a resource identified by ID.
     * This endpoint demonstrates PUT method support in CORS.
     *
     * @param id the resource ID
     * @param data the updated data
     * @return ResponseEntity with update confirmation
     */
    @PutMapping("/data/{id}")
    public ResponseEntity<String> updateData(@PathVariable Long id, @RequestBody String data) {
        return ResponseEntity.ok("Updated resource with ID: " + id + " with data: " + data);
    }

    /**
     * Handles DELETE requests to /api/data/{id}.
     * Simulates deleting a resource identified by ID.
     * This endpoint demonstrates DELETE method support in CORS.
     *
     * @param id the resource ID to delete
     * @return ResponseEntity with deletion confirmation
     */
    @DeleteMapping("/data/{id}")
    public ResponseEntity<String> deleteData(@PathVariable Long id) {
        return ResponseEntity.ok("Deleted resource with ID: " + id);
    }
}
