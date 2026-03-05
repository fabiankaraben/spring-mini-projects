package com.example.thymeleafbasicui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Thymeleaf Basic UI mini-project.
 *
 * <p>
 * This application demonstrates how Spring Boot integrates with the Thymeleaf
 * template engine to render dynamic HTML pages on the server side. Instead of
 * returning JSON (as a REST API does), the Spring MVC controllers here return
 * the name of a Thymeleaf template (an HTML file) together with model
 * attributes that Thymeleaf uses to populate the page before sending it to the
 * browser.
 * </p>
 *
 * <p>
 * Key concepts illustrated:
 * <ul>
 * <li>{@code @Controller} - marks a class as an MVC controller that returns
 * view names</li>
 * <li>{@code Model} / {@code ModelAndView} - carries data from the controller
 * to the template</li>
 * <li>Thymeleaf expressions ({@code th:text}, {@code th:each}, {@code th:if},
 * etc.)</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
public class ThymeleafBasicUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThymeleafBasicUiApplication.class, args);
    }
}
