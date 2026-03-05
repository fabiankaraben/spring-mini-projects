package com.example.springdatajpasetup.controller;

import com.example.springdatajpasetup.model.Book;
import com.example.springdatajpasetup.service.BookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced integration test for the Web layer using @WebMvcTest.
 * We mock the BookService layer since we are only testing the web layer
 * mechanics.
 */
@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // We replace @MockBean with @MockitoBean as recommended for newer Spring Boot
    // standards
    @MockitoBean
    private BookService bookService;

    @Test
    void testGetAllBooks() throws Exception {
        Book book1 = new Book("Title 1", "Author 1", 10.0);
        book1.setId(1L);

        when(bookService.getAllBooks()).thenReturn(Arrays.asList(book1));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Title 1"))
                .andExpect(jsonPath("$[0].author").value("Author 1"));
    }

    @Test
    void testGetBookByIdFound() throws Exception {
        Book book = new Book("Title 2", "Author 2", 20.0);
        book.setId(2L);

        when(bookService.getBookById(2L)).thenReturn(Optional.of(book));

        mockMvc.perform(get("/api/books/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Title 2"))
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void testGetBookByIdNotFound() throws Exception {
        when(bookService.getBookById(3L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/books/3"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateBook() throws Exception {
        Book book = new Book("New Book", "New Author", 15.0);
        book.setId(10L);

        when(bookService.saveBook(any(Book.class))).thenReturn(book);

        String bookJson = "{\"title\":\"New Book\",\"author\":\"New Author\",\"price\":15.0}";

        mockMvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("New Book"));
    }

    @Test
    void testDeleteBook() throws Exception {
        // Here we test that a delete request gives 204 No Content
        mockMvc.perform(delete("/api/books/1"))
                .andExpect(status().isNoContent());
    }
}
