package com.example.twiliosmssender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Twilio SMS Sender Spring Boot application.
 *
 * <p>This application demonstrates how to integrate the Twilio REST API with
 * Spring Boot to send SMS text messages programmatically. It exposes a REST API
 * that accepts a recipient phone number and message body, forwards the request to
 * Twilio, and persists a local record of every message sent (or failed).
 *
 * <p>{@code @SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Configuration} – marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – enables Spring Boot's auto-configuration.</li>
 *   <li>{@code @ComponentScan} – scans this package and sub-packages for components.</li>
 * </ul>
 */
@SpringBootApplication
public class TwilioSmsSenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(TwilioSmsSenderApplication.class, args);
    }
}
