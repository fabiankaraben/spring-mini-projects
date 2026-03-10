package com.example.javamailsender.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Bean Validation constraints on the request DTOs.
 *
 * <p>These tests exercise the validation annotations ({@code @NotBlank}, {@code @Email})
 * directly on the record types, without starting a Spring context or making HTTP calls.
 * The {@link Validator} is obtained from the default Hibernate Validator implementation
 * bundled with the {@code spring-boot-starter-validation} dependency.
 *
 * <h2>Why test DTO validation here?</h2>
 * <ul>
 *   <li>Faster feedback: no Spring context startup overhead.</li>
 *   <li>Focused: verifies that the correct constraint annotations are present
 *       and that the messages are meaningful.</li>
 *   <li>Avoids regression: ensures a refactor does not accidentally remove
 *       a constraint on a public API field.</li>
 * </ul>
 */
@DisplayName("Request DTO Bean Validation unit tests")
class EmailRequestValidationTest {

    /**
     * Hibernate Validator instance shared across all tests.
     * Created once in {@link BeforeAll} for efficiency.
     */
    private static Validator validator;

    /**
     * Bootstrap the default Hibernate Validator factory and create a {@link Validator}.
     *
     * <p>This mirrors what Spring MVC does internally when it processes a request
     * annotated with {@code @Valid}.
     */
    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ── PlainTextEmailRequest ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PlainTextEmailRequest – valid request has no violations")
    void plainText_valid_noViolations() {
        // Given: a fully-populated, valid request
        PlainTextEmailRequest request = new PlainTextEmailRequest(
                "user@example.com",
                "Test subject",
                "Test body"
        );

        // When: we validate the request
        Set<ConstraintViolation<PlainTextEmailRequest>> violations = validator.validate(request);

        // Then: no constraint violations
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("PlainTextEmailRequest – blank 'to' field produces violation")
    void plainText_blankTo_producesViolation() {
        PlainTextEmailRequest request = new PlainTextEmailRequest("", "Subject", "Body");

        Set<ConstraintViolation<PlainTextEmailRequest>> violations = validator.validate(request);

        // @NotBlank and @Email both fire on a blank string
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("to"));
    }

    @Test
    @DisplayName("PlainTextEmailRequest – invalid email format in 'to' field produces violation")
    void plainText_invalidEmailFormat_producesViolation() {
        PlainTextEmailRequest request = new PlainTextEmailRequest(
                "not-an-email",
                "Subject",
                "Body"
        );

        Set<ConstraintViolation<PlainTextEmailRequest>> violations = validator.validate(request);

        // @Email fires on an invalid format
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("to") &&
                v.getMessage().contains("valid email address")
        );
    }

    @Test
    @DisplayName("PlainTextEmailRequest – blank 'subject' field produces violation")
    void plainText_blankSubject_producesViolation() {
        PlainTextEmailRequest request = new PlainTextEmailRequest(
                "user@example.com",
                "  ",  // blank
                "Body"
        );

        Set<ConstraintViolation<PlainTextEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("subject")
        );
    }

    @Test
    @DisplayName("PlainTextEmailRequest – blank 'body' field produces violation")
    void plainText_blankBody_producesViolation() {
        PlainTextEmailRequest request = new PlainTextEmailRequest(
                "user@example.com",
                "Subject",
                ""   // blank
        );

        Set<ConstraintViolation<PlainTextEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("body")
        );
    }

    // ── HtmlEmailRequest ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("HtmlEmailRequest – valid request has no violations")
    void html_valid_noViolations() {
        HtmlEmailRequest request = new HtmlEmailRequest(
                "user@example.com",
                "Welcome!",
                "John",
                "Welcome to the platform."
        );

        Set<ConstraintViolation<HtmlEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("HtmlEmailRequest – blank 'to' field produces violation")
    void html_blankTo_producesViolation() {
        HtmlEmailRequest request = new HtmlEmailRequest("", "Subject", "Name", "Message");

        Set<ConstraintViolation<HtmlEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("to"));
    }

    @Test
    @DisplayName("HtmlEmailRequest – invalid email format in 'to' field produces violation")
    void html_invalidEmailFormat_producesViolation() {
        HtmlEmailRequest request = new HtmlEmailRequest(
                "invalid-email",
                "Subject",
                "Name",
                "Message"
        );

        Set<ConstraintViolation<HtmlEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("to") &&
                v.getMessage().contains("valid email address")
        );
    }

    @Test
    @DisplayName("HtmlEmailRequest – blank 'recipientName' produces violation")
    void html_blankRecipientName_producesViolation() {
        HtmlEmailRequest request = new HtmlEmailRequest(
                "user@example.com",
                "Subject",
                "",    // blank
                "Message"
        );

        Set<ConstraintViolation<HtmlEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("recipientName")
        );
    }

    @Test
    @DisplayName("HtmlEmailRequest – blank 'message' produces violation")
    void html_blankMessage_producesViolation() {
        HtmlEmailRequest request = new HtmlEmailRequest(
                "user@example.com",
                "Subject",
                "Name",
                "   "  // blank
        );

        Set<ConstraintViolation<HtmlEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("message")
        );
    }
}
