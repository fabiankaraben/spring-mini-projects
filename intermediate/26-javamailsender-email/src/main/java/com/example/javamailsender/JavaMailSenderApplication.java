package com.example.javamailsender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the JavaMailSender Email mini-project.
 *
 * <p>This Spring Boot application demonstrates how to send both plain text and
 * HTML emails using Spring's {@link org.springframework.mail.javamail.JavaMailSender}
 * abstraction, which wraps the Jakarta Mail (formerly JavaMail) API.
 *
 * <h2>Key concepts demonstrated</h2>
 * <ul>
 *   <li><strong>Plain text email</strong> – sent via
 *       {@link org.springframework.mail.SimpleMailMessage}, the simplest way to
 *       dispatch a text-only message without any MIME overhead.</li>
 *   <li><strong>HTML email</strong> – built with
 *       {@link org.springframework.mail.javamail.MimeMessageHelper} and a
 *       Thymeleaf HTML template, allowing styled, rich-content messages.</li>
 *   <li><strong>Thymeleaf templating</strong> – the HTML email body is rendered
 *       from a {@code .html} template, so the email layout is cleanly separated
 *       from Java code.</li>
 *   <li><strong>REST API</strong> – each email type is exposed as a POST endpoint
 *       so clients can trigger sends via HTTP.</li>
 * </ul>
 */
@SpringBootApplication
public class JavaMailSenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaMailSenderApplication.class, args);
    }
}
