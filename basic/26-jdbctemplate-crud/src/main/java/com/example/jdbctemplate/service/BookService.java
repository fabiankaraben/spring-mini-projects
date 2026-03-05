package com.example.jdbctemplate.service;

import com.example.jdbctemplate.domain.Book;
import com.example.jdbctemplate.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service class handling the business logic for Books.
 */
@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public Optional<Book> getBookById(Long id) {
        return bookRepository.findById(id);
    }

    public Book createBook(Book book) {
        return bookRepository.save(book);
    }

    public Optional<Book> updateBook(Long id, Book bookDetails) {
        return bookRepository.findById(id).map(existingBook -> {
            existingBook.setTitle(bookDetails.getTitle());
            existingBook.setAuthor(bookDetails.getAuthor());
            existingBook.setPublishedYear(bookDetails.getPublishedYear());
            bookRepository.update(existingBook);
            return existingBook;
        });
    }

    public boolean deleteBook(Long id) {
        return bookRepository.deleteById(id) > 0;
    }
}
