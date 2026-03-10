package com.example.javamailsender.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for sending a plain text email.
 *
 * <p>Bean Validation annotations ensure that all required fields are present
 * and properly formatted before the request reaches the service layer.
 * Spring MVC enforces these constraints when {@code @Valid} is used on the
 * controller method parameter.
 *
 * @param to      Recipient email address (must be a valid RFC 5321 address).
 * @param subject Subject line of the email (must not be blank).
 * @param body    Plain text content of the email (must not be blank).
 */
public record PlainTextEmailRequest(

        /**
         * Recipient's email address.
         * {@code @Email} validates the format (e.g., user@example.com).
         * {@code @NotBlank} ensures the field is present and non-empty.
         */
        @NotBlank(message = "Recipient email address must not be blank")
        @Email(message = "Recipient email address must be a valid email address")
        String to,

        /**
         * Subject line of the email.
         * Must not be blank — an empty subject is confusing for recipients.
         */
        @NotBlank(message = "Email subject must not be blank")
        String subject,

        /**
         * Plain text body of the email.
         * Must not be blank — sending an empty email is meaningless.
         */
        @NotBlank(message = "Email body must not be blank")
        String body
) {
}
