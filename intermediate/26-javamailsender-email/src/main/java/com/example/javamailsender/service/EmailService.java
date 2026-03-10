package com.example.javamailsender.service;

import com.example.javamailsender.dto.HtmlEmailRequest;
import com.example.javamailsender.dto.PlainTextEmailRequest;

/**
 * Contract for the email sending service.
 *
 * <p>Defining the service as an interface allows the implementation to be
 * swapped (e.g., replaced with a mock in unit tests) without changing the
 * controller or any other consumer of this service.
 *
 * <p>Two flavours of email are supported:
 * <ul>
 *   <li><strong>Plain text</strong> – raw text body, zero HTML overhead,
 *       ideal for simple notifications or alerts.</li>
 *   <li><strong>HTML</strong> – rich, styled body rendered from a Thymeleaf
 *       template, suitable for newsletters or formatted notifications.</li>
 * </ul>
 */
public interface EmailService {

    /**
     * Sends a plain text email using {@link org.springframework.mail.SimpleMailMessage}.
     *
     * <p>{@link org.springframework.mail.SimpleMailMessage} is the simplest
     * Spring Mail abstraction: no MIME parts, no attachments, no HTML — just a
     * sender, recipient, subject, and text body.
     *
     * @param request DTO containing the recipient address, subject, and body text.
     * @throws org.springframework.mail.MailException (unchecked) if the SMTP
     *         server rejects or cannot deliver the message.
     */
    void sendPlainText(PlainTextEmailRequest request);

    /**
     * Sends an HTML email built from a Thymeleaf template.
     *
     * <p>Internally, this method:
     * <ol>
     *   <li>Populates a Thymeleaf {@link org.thymeleaf.context.Context} with
     *       the request fields (recipientName, message).</li>
     *   <li>Processes the HTML template to produce the final HTML string.</li>
     *   <li>Creates a {@link jakarta.mail.internet.MimeMessage} and uses
     *       {@link org.springframework.mail.javamail.MimeMessageHelper} to
     *       set {@code html=true} on the message body.</li>
     * </ol>
     *
     * @param request DTO containing the recipient, subject, personalization
     *                variables, and the main message text.
     * @throws jakarta.mail.MessagingException (wrapped) if MimeMessage building fails.
     */
    void sendHtml(HtmlEmailRequest request);
}
