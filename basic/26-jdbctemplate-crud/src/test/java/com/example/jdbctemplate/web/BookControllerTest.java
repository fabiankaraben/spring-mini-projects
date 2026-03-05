package com.example.jdbctemplate.web;

import com.example.jdbctemplate.domain.Book;
import com.example.jdbctemplate.service.BookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for the BookController.
 * Uses @WebMvcTest for sliced testing of the web layer.
 */
@WebMvcTest(BookController.class)
public class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Use the new @MockitoBean instead of the deprecated @MockBean
    @MockitoBean
    private BookService bookService;

    @Test
    void shouldReturnAllBooks() throws Exception {
        Book book = new Book(1L, "Spring in Action", "Craig Walls", 2020);
        when(bookService.getAllBooks()).thenReturn(List.of(book));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Spring in Action"));
    }

    @Test
    void shouldReturnBookById() throws Exception {
        Book book = new Book(1L, "Spring in Action", "Craig Walls", 2020);
        when(bookService.getBookById(1L)).thenReturn(Optional.of(book));

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("Craig Walls"));
    }

    @Test
    void shouldCreateBook() throws Exception {
        Book savedBook = new Book(1L, "Effective Java", "Joshua Bloch", 2018);
        when(bookService.createBook(any(Book.class))).thenReturn(savedBook);

        mockMvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Effective Java\", \"author\": \"Joshua Bloch\", \"publishedYear\": 2018}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }
}
