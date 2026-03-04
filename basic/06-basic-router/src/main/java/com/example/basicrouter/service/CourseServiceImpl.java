package com.example.basicrouter.service;

import com.example.basicrouter.model.Course;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of the CourseService using an in-memory Map for storage.
 * The @Service annotation marks this class as a Spring component, meaning
 * Spring will manage its lifecycle and inject it where needed.
 */
@Service
public class CourseServiceImpl implements CourseService {

    // Simulating a database with a thread-safe map
    private final Map<Long, Course> courseDatabase = new ConcurrentHashMap<>();

    // Simulating database auto-incrementing ID
    private final AtomicLong idGenerator = new AtomicLong(1);

    public CourseServiceImpl() {
        // Pre-populate with some initial data for demonstration purposes
        save(new Course(null, "Spring Boot Basics", "Learn the fundamentals of Spring Boot."));
        save(new Course(null, "Advanced Java", "Deep dive into Java 21 features."));
    }

    @Override
    public List<Course> findAll() {
        return new ArrayList<>(courseDatabase.values());
    }

    @Override
    public Optional<Course> findById(Long id) {
        return Optional.ofNullable(courseDatabase.get(id));
    }

    @Override
    public Course save(Course course) {
        // Assign a new ID if it's a new course (id is null)
        Long courseId = course.id() == null ? idGenerator.getAndIncrement() : course.id();
        Course savedCourse = new Course(courseId, course.title(), course.description());
        courseDatabase.put(courseId, savedCourse);
        return savedCourse;
    }

    @Override
    public Course update(Long id, Course course) {
        if (!courseDatabase.containsKey(id)) {
            // Return null to signify that the course was not found
            return null;
        }
        Course updatedCourse = new Course(id, course.title(), course.description());
        courseDatabase.put(id, updatedCourse);
        return updatedCourse;
    }

    @Override
    public void delete(Long id) {
        courseDatabase.remove(id);
    }
}
