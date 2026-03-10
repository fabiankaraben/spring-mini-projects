package com.example.feignclientintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for the Feign Client Integration mini-project.
 *
 * <p>This application demonstrates how to use Spring Cloud OpenFeign to create
 * declarative HTTP clients. Instead of manually crafting HTTP requests with
 * RestTemplate or WebClient, OpenFeign lets you define a Java interface annotated
 * with Spring MVC annotations, and Spring Cloud generates the HTTP client
 * implementation automatically at startup.
 *
 * <p>Architecture overview:
 * <pre>
 *   HTTP Request
 *        │
 *        ▼
 *   PostController          – REST endpoints exposed to callers
 *        │
 *        ▼
 *   PostService             – orchestrates business logic
 *        │
 *        ▼
 *   JsonPlaceholderClient   – OpenFeign declarative HTTP client
 *        │  (HTTP)
 *        ▼
 *   JSONPlaceholder API     – upstream REST API (https://jsonplaceholder.typicode.com)
 * </pre>
 *
 * <p>{@link EnableFeignClients} scans this package (and sub-packages) for interfaces
 * annotated with {@link org.springframework.cloud.openfeign.FeignClient} and registers
 * them as Spring beans. Without this annotation, no Feign proxies are created and the
 * {@code @Autowired} injection of Feign client interfaces would fail at startup.
 */
@SpringBootApplication
@EnableFeignClients  // Scans for @FeignClient interfaces and creates proxy beans
public class FeignClientIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeignClientIntegrationApplication.class, args);
    }
}
