package com.example.twiliosmssender.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO (Data Transfer Object) for the "send SMS" HTTP request body.
 *
 * <p>Clients send this JSON payload when calling {@code POST /api/sms/send}.
 *
 * <p>Example request body:
 * <pre>{@code
 * {
 *   "to": "+15551234567",
 *   "body": "Hello from Spring Boot + Twilio!"
 * }
 * }</pre>
 *
 * <p>Bean Validation annotations ensure the data is well-formed before
 * it reaches the service layer, returning HTTP 400 for invalid requests.
 *
 * @param to   the recipient's phone number in E.164 format (e.g., "+15551234567")
 * @param body the text content of the SMS message (1–1600 characters)
 */
public record SendSmsRequest(

        /**
         * The recipient phone number in E.164 international format.
         *
         * <p>E.164 format rules:
         * <ul>
         *   <li>Starts with "+" followed by the country code</li>
         *   <li>Followed by the subscriber number (no spaces, dashes, or parentheses)</li>
         *   <li>Total length: 8–15 digits after the "+"</li>
         * </ul>
         *
         * <p>Examples: {@code +15551234567} (US), {@code +441234567890} (UK),
         * {@code +5491112345678} (Argentina).
         */
        @NotBlank(message = "to (recipient phone number) is required")
        @Pattern(
                regexp = "^\\+[1-9]\\d{7,14}$",
                message = "to must be a valid E.164 phone number (e.g. +15551234567)"
        )
        String to,

        /**
         * The text content of the SMS message.
         *
         * <p>Standard SMS messages are 160 characters. Longer messages are
         * automatically split by Twilio into multiple segments (up to 1600 chars total).
         */
        @NotBlank(message = "body (message text) is required")
        @Size(min = 1, max = 1600, message = "body must be between 1 and 1600 characters")
        String body

) {
}
