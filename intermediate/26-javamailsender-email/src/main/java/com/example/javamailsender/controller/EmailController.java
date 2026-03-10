package com.example.javamailsender.controller;

import com.example.javamailsender.dto.EmailResponse;
import com.example.javamailsender.dto.HtmlEmailRequest;
import com.example.javamailsender.dto.PlainTextEmailRequest;
import com.example.javamailsender.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes endpoints for sending emails.
 *
 * <p>This controller handles two POST endpoints:
 * <ul>
 *   <li>{@code POST /api/email/plain} – sends a plain text email.</li>
 *   <li>{@code POST /api/email/html}  – sends an HTML email rendered from
 *       a Thymeleaf template.</li>
 * </ul>
 *
 * <p>Validation is applied at the controller boundary via {@code @Valid}.
 * Any constraint violations (missing fields, invalid email format, etc.)
 * are handled globally by {@link GlobalExceptionHandler}.
 *
 * <p>The controller intentionally contains no business logic — it only
 * validates the incoming request, delegates to {@link EmailService}, and
 * maps the outcome to an HTTP response.
 */
@RestController
@RequestMapping("/api/email")
public class EmailController {

    /**
     * The email service that handles the actual SMTP communication.
     * Injected via constructor to keep the controller testable with mocks.
     */
    private final EmailService emailService;

    /**
     * Constructor injection of the email service.
     *
     * @param emailService the service implementation for sending emails.
     */
    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Sends a plain text email.
     *
     * <p>Accepts a JSON body matching {@link PlainTextEmailRequest}.
     * {@code @Valid} triggers Bean Validation before the method body runs;
     * invalid requests are rejected with a 400 response.
     *
     * <p>On success, returns HTTP 200 with an {@link EmailResponse} body.
     * On SMTP failure, the unchecked {@code MailException} propagates to
     * {@link GlobalExceptionHandler} which maps it to a 502 response.
     *
     * @param request validated request DTO from the JSON body.
     * @return 200 OK with a confirmation {@link EmailResponse}.
     *
     * <p><strong>Example request:</strong>
     * <pre>{@code
     * POST /api/email/plain
     * {
     *   "to": "user@example.com",
     *   "subject": "Hello",
     *   "body": "This is a plain text email."
     * }
     * }</pre>
     */
    @PostMapping("/plain")
    public ResponseEntity<EmailResponse> sendPlainText(@Valid @RequestBody PlainTextEmailRequest request) {
        // Delegate to the service; any MailException bubbles to GlobalExceptionHandler
        emailService.sendPlainText(request);

        // Build a success response confirming the recipient address
        EmailResponse response = new EmailResponse("sent", request.to(), "Email sent successfully.");
        return ResponseEntity.ok(response);
    }

    /**
     * Sends an HTML email rendered from a Thymeleaf template.
     *
     * <p>Accepts a JSON body matching {@link HtmlEmailRequest}.
     * The {@code recipientName} and {@code message} fields are injected
     * into the HTML template as Thymeleaf variables.
     *
     * <p>On success, returns HTTP 200 with an {@link EmailResponse} body.
     *
     * @param request validated request DTO from the JSON body.
     * @return 200 OK with a confirmation {@link EmailResponse}.
     *
     * <p><strong>Example request:</strong>
     * <pre>{@code
     * POST /api/email/html
     * {
     *   "to": "user@example.com",
     *   "subject": "Welcome!",
     *   "recipientName": "John",
     *   "message": "Welcome to our platform!"
     * }
     * }</pre>
     */
    @PostMapping("/html")
    public ResponseEntity<EmailResponse> sendHtml(@Valid @RequestBody HtmlEmailRequest request) {
        // Delegate to the service; any MailException/RuntimeException bubbles to GlobalExceptionHandler
        emailService.sendHtml(request);

        // Build a success response confirming the recipient address
        EmailResponse response = new EmailResponse("sent", request.to(), "HTML email sent successfully.");
        return ResponseEntity.ok(response);
    }
}
