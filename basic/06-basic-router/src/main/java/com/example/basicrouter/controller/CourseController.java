package com.example.basicrouter.controller;

import com.example.basicrouter.model.Course;
import com.example.basicrouter.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller showcasing various HTTP mapping annotations.
 * The @RestController annotation combines @Controller and @ResponseBody,
 * meaning returned objects are automatically serialized into the HTTP response
 * body (e.g., as JSON).
 */
@RestController
@RequestMapping("/api/courses") // Base path for all endpoints in this controller
public class CourseController {

    private final CourseService courseService;

    // Constructor injection is recommended over @Autowired field injection
    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * Handles HTTP GET requests to retrieve all courses.
     * Maps to: GET /api/courses
     */
    @GetMapping
    public List<Course> getAllCourses() {
        return courseService.findAll();
    }

    /**
     * Handles HTTP GET requests to retrieve a specific course by its ID.
     * The {id} is a path variable extracted via @PathVariable.
     * Maps to: GET /api/courses/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Course> getCourseById(@PathVariable Long id) {
        return courseService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Handles HTTP POST requests to create a new course.
     * The @RequestBody annotation maps the HTTP request body to the Course object.
     * Maps to: POST /api/courses
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // Sets the response status to 201 Created
    public Course createCourse(@RequestBody Course course) {
        return courseService.save(course);
    }

    /**
     * Handles HTTP PUT requests to update an existing course fully.
     * Maps to: PUT /api/courses/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Course> updateCourse(@PathVariable Long id, @RequestBody Course course) {
        Course updated = courseService.update(id, course);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    /**
     * Handles HTTP DELETE requests to remove a course.
     * Maps to: DELETE /api/courses/{id}
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // Sets response to 204 No Content
    public void deleteCourse(@PathVariable Long id) {
        courseService.delete(id);
    }
}
