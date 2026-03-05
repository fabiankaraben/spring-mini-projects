package com.example.jdbctemplate.service;

import com.example.jdbctemplate.domain.Book;
import com.example.jdbctemplate.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BookService.
 */
@ExtendWith(MockitoExtension.class)
public class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    @Test
    void shouldCreateBook() {
        Book bookToSave = new Book(null, "Test Title", "Author", 2025);
        Book savedBook = new Book(1L, "Test Title", "Author", 2025);

        when(bookRepository.save(any(Book.class))).thenReturn(savedBook);

        Book result = bookService.createBook(bookToSave);
        assertNotNull(result.getId());
        assertEquals("Test Title", result.getTitle());
        verify(bookRepository, times(1)).save(bookToSave);
    }

    @Test
    void shouldUpdateBookIfExists() {
        Book existingBook = new Book(1L, "Old Title", "Old Author", 2020);
        Book newDetails = new Book(null, "New Title", "New Author", 2025);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(existingBook));
        when(bookRepository.update(any(Book.class))).thenReturn(1);

        Optional<Book> result = bookService.updateBook(1L, newDetails);

        assertTrue(result.isPresent());
        assertEquals("New Title", result.get().getTitle());
        verify(bookRepository, times(1)).update(existingBook);
    }

    @Test
    void shouldDeleteBook() {
        when(bookRepository.deleteById(1L)).thenReturn(1);

        boolean isDeleted = bookService.deleteBook(1L);

        assertTrue(isDeleted);
        verify(bookRepository, times(1)).deleteById(1L);
    }
}
