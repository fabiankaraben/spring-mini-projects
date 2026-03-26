package com.example.camundabpm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Camunda BPM Spring Boot application.
 *
 * <p>@SpringBootApplication enables:
 * <ul>
 *   <li>@Configuration — marks this class as a source of bean definitions.</li>
 *   <li>@EnableAutoConfiguration — activates Spring Boot's auto-configuration,
 *       including Camunda's CamundaBpmAutoConfiguration which sets up the process
 *       engine, deploys BPMN resources from the classpath, and registers all
 *       Camunda services (RuntimeService, TaskService, etc.) as Spring beans.</li>
 *   <li>@ComponentScan — scans this package and sub-packages for @Component,
 *       @Service, @Repository, @Controller, and @RestController classes.</li>
 * </ul>
 *
 * <p>The Camunda engine is auto-configured by the camunda-bpm-spring-boot-starter.
 * It reads {@code application.yml} for engine settings and auto-deploys all
 * {@code *.bpmn} files found on the classpath.
 */
@SpringBootApplication
public class CamundaBpmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CamundaBpmApplication.class, args);
    }
}
