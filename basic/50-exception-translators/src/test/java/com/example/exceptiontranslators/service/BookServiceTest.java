package com.example.exceptiontranslators.service;

import com.example.exceptiontranslators.entity.Book;
import com.example.exceptiontranslators.exception.BookAlreadyExistsException;
import com.example.exceptiontranslators.exception.BookNotFoundException;
import com.example.exceptiontranslators.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    @Test
    void createBook_ShouldReturnSavedBook_WhenIsbnDoesNotExist() {
        Book book = new Book(null, "1234567890", "Test Book", "Test Author");
        when(bookRepository.existsByIsbn(book.getIsbn())).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenReturn(book);

        Book savedBook = bookService.createBook(book);

        assertNotNull(savedBook);
        assertEquals("1234567890", savedBook.getIsbn());
        verify(bookRepository).save(book);
    }

    @Test
    void createBook_ShouldThrowException_WhenIsbnAlreadyExists() {
        Book book = new Book(null, "1234567890", "Test Book", "Test Author");
        when(bookRepository.existsByIsbn(book.getIsbn())).thenReturn(true);

        assertThrows(BookAlreadyExistsException.class, () -> bookService.createBook(book));
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void getBookByIsbn_ShouldReturnBook_WhenItExists() {
        String isbn = "1234567890";
        Book book = new Book(1L, isbn, "Test Book", "Test Author");
        when(bookRepository.findByIsbn(isbn)).thenReturn(Optional.of(book));

        Book foundBook = bookService.getBookByIsbn(isbn);

        assertNotNull(foundBook);
        assertEquals(isbn, foundBook.getIsbn());
    }

    @Test
    void getBookByIsbn_ShouldThrowException_WhenItDoesNotExist() {
        String isbn = "1234567890";
        when(bookRepository.findByIsbn(isbn)).thenReturn(Optional.empty());

        assertThrows(BookNotFoundException.class, () -> bookService.getBookByIsbn(isbn));
    }
}
