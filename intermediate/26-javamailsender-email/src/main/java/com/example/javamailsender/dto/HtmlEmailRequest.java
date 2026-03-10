package com.example.javamailsender.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for sending an HTML email rendered from a Thymeleaf template.
 *
 * <p>In addition to the standard fields (to, subject), this DTO carries
 * {@code recipientName} and {@code message} which are injected into the
 * Thymeleaf HTML template as template variables. This separation of
 * content from layout keeps the controller clean and testable.
 *
 * @param to            Recipient email address (must be a valid RFC 5321 address).
 * @param subject       Subject line of the email (must not be blank).
 * @param recipientName Name used to personalise the greeting inside the template.
 * @param message       The main message body injected into the HTML template.
 */
public record HtmlEmailRequest(

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
         */
        @NotBlank(message = "Email subject must not be blank")
        String subject,

        /**
         * Recipient name injected into the Thymeleaf HTML template as a variable.
         * Used to personalise the greeting (e.g., "Dear John,").
         */
        @NotBlank(message = "Recipient name must not be blank")
        String recipientName,

        /**
         * Main message text injected into the HTML template body.
         */
        @NotBlank(message = "Message must not be blank")
        String message
) {
}
