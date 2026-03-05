package com.example.springdatajpasetup.repository;

import com.example.springdatajpasetup.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * By extending JpaRepository, Spring Data JPA automatically provides
 * common CRUD operations for the Book entity.
 * We don't need to write implementations for save(), findAll(), findById(),
 * etc.
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
}
