package com.example.basicrouter.service;

import com.example.basicrouter.model.Course;
import java.util.List;
import java.util.Optional;

/**
 * Service interface defining the business logic for managing courses.
 * In a real application, this would likely interact with a database repository.
 */
public interface CourseService {
    List<Course> findAll();

    Optional<Course> findById(Long id);

    Course save(Course course);

    Course update(Long id, Course course);

    void delete(Long id);
}
