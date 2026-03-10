package com.example.javamailsender.service;

import com.example.javamailsender.dto.HtmlEmailRequest;
import com.example.javamailsender.dto.PlainTextEmailRequest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link EmailServiceImpl}.
 *
 * <p>These tests verify the <em>domain logic</em> of the email service in
 * complete isolation from the network and any SMTP server.
 *
 * <h2>Test strategy</h2>
 * <ul>
 *   <li>{@link JavaMailSender} is replaced by a Mockito mock so no real SMTP
 *       connection is ever made. We only verify the messages handed to it.</li>
 *   <li>{@link TemplateEngine} is replaced by a Mockito mock so HTML template
 *       rendering is predictable and fast (returns a canned string).</li>
 *   <li>{@link MimeMessage} is stubbed via {@link JavaMailSender#createMimeMessage()}
 *       to return a real {@link com.sun.mail.smtp.SMTPMessage} so that
 *       {@link org.springframework.mail.javamail.MimeMessageHelper} can
 *       populate it without a live Jakarta Mail session.</li>
 *   <li>{@link ReflectionTestUtils#setField} injects the {@code fromAddress}
 *       private field that would normally be set by {@code @Value}.</li>
 * </ul>
 *
 * <h2>What these tests verify</h2>
 * <ul>
 *   <li>Plain text emails are built with the correct from/to/subject/body.</li>
 *   <li>HTML emails are built and dispatched using a MimeMessage.</li>
 *   <li>A {@link RuntimeException} is thrown when {@link TemplateEngine} fails.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl unit tests")
class EmailServiceImplTest {

    /**
     * Mock of {@link JavaMailSender}.
     * No real SMTP calls are made — we only capture the message objects.
     */
    @Mock
    private JavaMailSender mailSender;

    /**
     * Mock of {@link TemplateEngine}.
     * Returns a canned HTML string instead of processing a real template file.
     */
    @Mock
    private TemplateEngine templateEngine;

    /** The system under test, constructed with the mocked dependencies. */
    private EmailServiceImpl emailService;

    /**
     * Recreate the service before each test, and inject the {@code fromAddress}
     * field that {@code @Value("${app.mail.from}")} normally provides.
     */
    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender, templateEngine);
        // Inject the private @Value field without starting a Spring context
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@test.com");
    }

    // ── sendPlainText ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendPlainText – sends a SimpleMailMessage with correct from/to/subject/body")
    void sendPlainText_sendsSimpleMailMessageWithCorrectFields() {
        // Given: a valid plain text email request
        PlainTextEmailRequest request = new PlainTextEmailRequest(
                "recipient@example.com",
                "Hello from test",
                "This is the email body."
        );

        // When: the service sends the email
        emailService.sendPlainText(request);

        // Then: verify that JavaMailSender.send(SimpleMailMessage) was called once
        // and capture the message to inspect its fields
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("noreply@test.com");
        assertThat(sent.getTo()).containsExactly("recipient@example.com");
        assertThat(sent.getSubject()).isEqualTo("Hello from test");
        assertThat(sent.getText()).isEqualTo("This is the email body.");
    }

    @Test
    @DisplayName("sendPlainText – propagates MailException from JavaMailSender")
    void sendPlainText_propagatesMailException() {
        // Given: the mail sender throws a MailException (e.g., SMTP unavailable)
        PlainTextEmailRequest request = new PlainTextEmailRequest(
                "recipient@example.com",
                "Subject",
                "Body"
        );
        // send(SimpleMailMessage) returns void, so we must use willThrow().given() (BDD void syntax)
        org.mockito.BDDMockito.willThrow(new org.springframework.mail.MailSendException("SMTP error"))
                .given(mailSender).send(any(SimpleMailMessage.class));

        // When / Then: the exception propagates to the caller (handled by GlobalExceptionHandler)
        assertThatThrownBy(() -> emailService.sendPlainText(request))
                .isInstanceOf(org.springframework.mail.MailException.class)
                .hasMessageContaining("SMTP error");
    }

    // ── sendHtml ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendHtml – creates a MimeMessage and calls mailSender.send(MimeMessage)")
    void sendHtml_createsMimeMessageAndCallsSend() {
        // Given: a real MimeMessage instance (jakarta.mail) for MimeMessageHelper to populate
        MimeMessage mimeMessage = mock(MimeMessage.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);

        // The template engine returns a canned HTML string
        given(templateEngine.process(anyString(), any(Context.class)))
                .willReturn("<html><body>Hello John</body></html>");

        HtmlEmailRequest request = new HtmlEmailRequest(
                "recipient@example.com",
                "Welcome!",
                "John",
                "Welcome to our platform!"
        );

        // When: the service builds and sends the HTML email
        emailService.sendHtml(request);

        // Then: mailSender.send(MimeMessage) must be called once
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendHtml – passes recipientName and message to the Thymeleaf context")
    void sendHtml_passesCorrectVariablesToThymeleafContext() {
        // Given
        MimeMessage mimeMessage = mock(MimeMessage.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);

        // Capture the Thymeleaf Context passed to the template engine
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        given(templateEngine.process(anyString(), contextCaptor.capture()))
                .willReturn("<html><body>Hello Alice</body></html>");

        HtmlEmailRequest request = new HtmlEmailRequest(
                "alice@example.com",
                "Greetings",
                "Alice",
                "Your account is ready."
        );

        // When
        emailService.sendHtml(request);

        // Then: verify that the template context contains the expected variables
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("recipientName")).isEqualTo("Alice");
        assertThat(capturedContext.getVariable("message")).isEqualTo("Your account is ready.");
    }

    @Test
    @DisplayName("sendHtml – throws RuntimeException when TemplateEngine fails")
    void sendHtml_throwsRuntimeExceptionWhenTemplateFails() {
        // Given: a valid MimeMessage but the template engine throws an exception
        MimeMessage mimeMessage = mock(MimeMessage.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        given(templateEngine.process(anyString(), any(Context.class)))
                .willThrow(new RuntimeException("Template not found"));

        HtmlEmailRequest request = new HtmlEmailRequest(
                "recipient@example.com",
                "Subject",
                "Bob",
                "Some message"
        );

        // When / Then: a RuntimeException propagates from the service
        assertThatThrownBy(() -> emailService.sendHtml(request))
                .isInstanceOf(RuntimeException.class);
    }
}
