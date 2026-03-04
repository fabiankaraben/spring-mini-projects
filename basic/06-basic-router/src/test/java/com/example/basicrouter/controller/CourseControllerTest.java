package com.example.basicrouter.controller;

import com.example.basicrouter.model.Course;
import com.example.basicrouter.service.CourseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced integration test for the CourseController.
 * 
 * @WebMvcTest auto-configures Spring MVC infrastructure and loads only the
 *             specified controller,
 *             making tests faster than @SpringBootTest.
 */
@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Use Spring Boot 3.4.x @MockitoBean instead of @MockBean
    @MockitoBean
    private CourseService courseService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturnAllCourses() throws Exception {
        // Arrange
        Course course1 = new Course(1L, "Course 1", "Desc 1");
        Course course2 = new Course(2L, "Course 2", "Desc 2");
        when(courseService.findAll()).thenReturn(Arrays.asList(course1, course2));

        // Act & Assert
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].title").value("Course 1"));
    }

    @Test
    void shouldReturnCourseByIdWhenExists() throws Exception {
        // Arrange
        Course course = new Course(1L, "Course 1", "Desc 1");
        when(courseService.findById(1L)).thenReturn(Optional.of(course));

        // Act & Assert
        mockMvc.perform(get("/api/courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Course 1"));
    }

    @Test
    void shouldReturnNotFoundWhenCourseByIdDoesNotExist() throws Exception {
        // Arrange
        when(courseService.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/courses/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateCourse() throws Exception {
        // Arrange
        Course inputCourse = new Course(null, "New Course", "New Desc");
        Course savedCourse = new Course(3L, "New Course", "New Desc");
        when(courseService.save(any(Course.class))).thenReturn(savedCourse);

        // Act & Assert
        mockMvc.perform(post("/api/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputCourse)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.title").value("New Course"));
    }

    @Test
    void shouldUpdateCourse() throws Exception {
        // Arrange
        Course inputCourse = new Course(1L, "Updated Course", "Updated Desc");
        when(courseService.update(eq(1L), any(Course.class))).thenReturn(inputCourse);

        // Act & Assert
        mockMvc.perform(put("/api/courses/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputCourse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Course"));
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingNonExistentCourse() throws Exception {
        // Arrange
        Course inputCourse = new Course(99L, "Updated Course", "Updated Desc");
        when(courseService.update(eq(99L), any(Course.class))).thenReturn(null);

        // Act & Assert
        mockMvc.perform(put("/api/courses/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputCourse)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteCourse() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/courses/1"))
                .andExpect(status().isNoContent());

        verify(courseService).delete(1L);
    }
}
