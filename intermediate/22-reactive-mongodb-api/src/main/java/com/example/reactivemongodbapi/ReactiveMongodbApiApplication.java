package com.example.reactivemongodbapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Reactive MongoDB API application.
 *
 * <p>{@link SpringBootApplication} is a meta-annotation that combines:
 * <ul>
 *   <li>{@code @Configuration} – marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – tells Spring Boot to automatically configure
 *       the application based on the dependencies present on the classpath (e.g.,
 *       WebFlux on the classpath triggers Netty server setup; Reactive MongoDB triggers
 *       {@code ReactiveMongoAutoConfiguration}).</li>
 *   <li>{@code @ComponentScan} – scans the current package and sub-packages for
 *       Spring-managed components ({@code @Controller}, {@code @Service},
 *       {@code @Repository}, etc.).</li>
 * </ul>
 *
 * <p><strong>Why Netty instead of Tomcat?</strong><br>
 * Spring WebFlux uses Netty (or Undertow) as its embedded server instead of Tomcat.
 * Tomcat is a thread-per-request server — it blocks a thread until the response is sent.
 * Netty uses an event-loop model with a small fixed pool of threads; I/O events are
 * dispatched to handlers without blocking the thread, enabling much higher concurrency
 * with fewer resources.
 *
 * <p><strong>Why Reactive MongoDB?</strong><br>
 * The traditional Spring Data MongoDB driver (synchronous) uses blocking I/O under the
 * hood — every database call blocks the calling thread until MongoDB responds. The
 * Reactive MongoDB driver (based on the MongoDB Reactive Streams driver) returns
 * {@code Publisher} types (implemented as {@link reactor.core.publisher.Mono} /
 * {@link reactor.core.publisher.Flux}) so the calling thread is never blocked,
 * preserving the fully non-blocking stack from HTTP all the way down to MongoDB.
 */
@SpringBootApplication
public class ReactiveMongodbApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReactiveMongodbApiApplication.class, args);
	}
}
