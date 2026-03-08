package com.example.formlogin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Form Login Security application.
 *
 * <p>This application demonstrates how to configure a browser-based
 * form login and logout flow using Spring Security. Key concepts covered:
 * <ul>
 *   <li>Custom HTML login page served by Spring Security</li>
 *   <li>Session-based authentication (stateful, as required by form login)</li>
 *   <li>Role-based access control (USER / ADMIN)</li>
 *   <li>Secure logout that invalidates the HTTP session and clears cookies</li>
 *   <li>Users persisted in PostgreSQL via Spring Data JPA</li>
 * </ul>
 */
@SpringBootApplication
public class FormLoginApplication {

    public static void main(String[] args) {
        SpringApplication.run(FormLoginApplication.class, args);
    }
}
