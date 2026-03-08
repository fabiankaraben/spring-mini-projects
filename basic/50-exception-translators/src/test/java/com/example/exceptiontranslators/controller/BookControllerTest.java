package com.example.exceptiontranslators.controller;

import com.example.exceptiontranslators.entity.Book;
import com.example.exceptiontranslators.exception.BookAlreadyExistsException;
import com.example.exceptiontranslators.exception.BookNotFoundException;
import com.example.exceptiontranslators.service.BookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createBook_ShouldReturnCreated_WhenValidRequest() throws Exception {
        Book book = new Book(null, "1234567890", "Test Book", "Test Author");
        when(bookService.createBook(any(Book.class))).thenReturn(book);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(book)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value("1234567890"));
    }

    @Test
    void createBook_ShouldReturnConflict_WhenBookAlreadyExists() throws Exception {
        Book book = new Book(null, "1234567890", "Test Book", "Test Author");
        when(bookService.createBook(any(Book.class))).thenThrow(new BookAlreadyExistsException("Book already exists"));

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(book)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createBook_ShouldReturnBadRequest_WhenValidationFails() throws Exception {
        Book book = new Book(null, "", "", ""); // Invalid book

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(book)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getBookByIsbn_ShouldReturnOk_WhenBookExists() throws Exception {
        Book book = new Book(1L, "1234567890", "Test Book", "Test Author");
        when(bookService.getBookByIsbn("1234567890")).thenReturn(book);

        mockMvc.perform(get("/api/books/1234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Book"));
    }

    @Test
    void getBookByIsbn_ShouldReturnNotFound_WhenBookDoesNotExist() throws Exception {
        when(bookService.getBookByIsbn("1234567890")).thenThrow(new BookNotFoundException("Book not found"));

        mockMvc.perform(get("/api/books/1234567890"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
