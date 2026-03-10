package com.example.javamailsender.service;

import com.example.javamailsender.dto.HtmlEmailRequest;
import com.example.javamailsender.dto.PlainTextEmailRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Default implementation of {@link EmailService}.
 *
 * <p>This class wraps Spring's {@link JavaMailSender} and the Thymeleaf
 * {@link TemplateEngine} to provide two kinds of email sending:
 *
 * <ul>
 *   <li><strong>Plain text</strong> via {@link SimpleMailMessage} – the
 *       lightweight, no-MIME path.</li>
 *   <li><strong>HTML</strong> via {@link MimeMessage} + {@link MimeMessageHelper}
 *       + Thymeleaf template rendering.</li>
 * </ul>
 *
 * <h2>Dependency injection</h2>
 * <p>{@link JavaMailSender} is auto-configured by Spring Boot when
 * {@code spring.mail.*} properties are present in {@code application.yml}.
 * The {@link TemplateEngine} bean is auto-configured by the Thymeleaf starter.
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    /**
     * Spring's JavaMailSender abstraction – auto-configured via
     * {@code spring.mail.host}, {@code spring.mail.port}, etc.
     * Internally wraps a Jakarta Mail {@link jakarta.mail.Session}.
     */
    private final JavaMailSender mailSender;

    /**
     * Thymeleaf template engine used to process HTML email templates.
     * Templates are resolved from {@code src/main/resources/templates/}.
     */
    private final TemplateEngine templateEngine;

    /**
     * The "from" address configured in {@code application.yml} under
     * {@code app.mail.from}. Injected via {@code @Value} so it can be
     * changed without modifying Java code.
     */
    @Value("${app.mail.from}")
    private String fromAddress;

    /**
     * Constructor injection ensures both dependencies are provided at
     * construction time, making the service easier to unit-test.
     *
     * @param mailSender     auto-configured {@link JavaMailSender} bean.
     * @param templateEngine auto-configured Thymeleaf {@link TemplateEngine} bean.
     */
    public EmailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses {@link SimpleMailMessage} – the simplest Spring Mail abstraction.
     * No MIME parts, no HTML encoding, just a plain text envelope.
     *
     * <p>Steps:
     * <ol>
     *   <li>Create a {@link SimpleMailMessage} instance.</li>
     *   <li>Set {@code from}, {@code to}, {@code subject}, and {@code text}.</li>
     *   <li>Delegate to {@link JavaMailSender#send(SimpleMailMessage)} which
     *       opens an SMTP connection and transmits the message.</li>
     * </ol>
     */
    @Override
    public void sendPlainText(PlainTextEmailRequest request) {
        log.info("Sending plain text email to: {}", request.to());

        // SimpleMailMessage is a value object — create a fresh instance per request
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(request.to());
        message.setSubject(request.subject());
        message.setText(request.body());

        // send() opens the SMTP connection, transmits, and closes it
        mailSender.send(message);

        log.info("Plain text email sent successfully to: {}", request.to());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses a {@link MimeMessage} (MIME multipart) so that the email body
     * can carry HTML content.
     *
     * <p>Steps:
     * <ol>
     *   <li>Create a {@link MimeMessage} via {@link JavaMailSender#createMimeMessage()}.</li>
     *   <li>Wrap it with {@link MimeMessageHelper} using {@code multipart=true}
     *       and {@code UTF-8} charset.</li>
     *   <li>Populate a Thymeleaf {@link Context} with template variables
     *       ({@code recipientName}, {@code message}).</li>
     *   <li>Process the template {@code email/html-email.html} to produce the
     *       final HTML string.</li>
     *   <li>Set the HTML body on the helper with {@code html=true}.</li>
     *   <li>Send via {@link JavaMailSender#send(MimeMessage)}.</li>
     * </ol>
     */
    @Override
    public void sendHtml(HtmlEmailRequest request) {
        log.info("Sending HTML email to: {}", request.to());

        try {
            // Create an empty MimeMessage through the mail sender
            MimeMessage mimeMessage = mailSender.createMimeMessage();

            // MimeMessageHelper simplifies building multipart MIME messages.
            // multipart=true enables both text and HTML parts (or attachments).
            // "UTF-8" ensures international characters are encoded correctly.
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(request.to());
            helper.setSubject(request.subject());

            // ── Thymeleaf template rendering ─────────────────────────────────────
            // Context holds the variables that the template can reference with
            // Thymeleaf expressions such as ${recipientName} and ${message}.
            Context context = new Context();
            context.setVariable("recipientName", request.recipientName());
            context.setVariable("message", request.message());

            // process() renders the template file located at:
            // src/main/resources/templates/email/html-email.html
            String htmlBody = templateEngine.process("email/html-email", context);

            // Set the rendered HTML as the email body (second arg true = HTML)
            helper.setText(htmlBody, true);

            // send() opens the SMTP connection, transmits, and closes it
            mailSender.send(mimeMessage);

            log.info("HTML email sent successfully to: {}", request.to());

        } catch (MessagingException e) {
            // Wrap the checked MessagingException in an unchecked RuntimeException
            // so callers (and the service interface) don't need to declare it.
            log.error("Failed to send HTML email to {}: {}", request.to(), e.getMessage(), e);
            throw new RuntimeException("Failed to build HTML email message", e);
        }
    }
}
