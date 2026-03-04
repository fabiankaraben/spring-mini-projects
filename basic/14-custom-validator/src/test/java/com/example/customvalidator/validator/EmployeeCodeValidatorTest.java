package com.example.customvalidator.validator;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit Test for Custom Validator Logic.
 * Verifies how regex handles string inputs.
 */
class EmployeeCodeValidatorTest {

    private EmployeeCodeValidator validator;
    private ConstraintValidatorContext mockContext;

    @BeforeEach
    void setUp() {
        validator = new EmployeeCodeValidator();
        // Context is required but its methods might not be invoked depending on exact
        // validation
        // Our simplest logic doesn't use the context natively so Mockito.mock()
        // performs nicely
        mockContext = Mockito.mock(ConstraintValidatorContext.class);
    }

    @Test
    @DisplayName("Should pass valid EMP code")
    void testValidEmployeeCode() {
        boolean valid = validator.isValid("EMP-1234", mockContext);
        assertTrue(valid, "EMP-1234 should be considered valid.");
    }

    @Test
    @DisplayName("Should fail empty or null employee code")
    void testNullOrEmptyEmployeeCode() {
        assertFalse(validator.isValid(null, mockContext), "Null should fail.");
        assertFalse(validator.isValid("", mockContext), "Empty string should fail.");
        assertFalse(validator.isValid("   ", mockContext), "Blank string should fail.");
    }

    @Test
    @DisplayName("Should fail employee code not prefixed by EMP-")
    void testMissingPrefixCode() {
        assertFalse(validator.isValid("1234", mockContext), "Digits only should fail.");
        assertFalse(validator.isValid("emp-1234", mockContext), "Lower case should fail prefix check.");
        assertFalse(validator.isValid("ADMIN-1234", mockContext), "Different prefix should fail.");
    }

    @Test
    @DisplayName("Should fail employee code not having exactly 4 digits")
    void testNon4DigitCode() {
        assertFalse(validator.isValid("EMP-123", mockContext), "Too short code should fail.");
        assertFalse(validator.isValid("EMP-12345", mockContext), "Too long code should fail.");
        assertFalse(validator.isValid("EMP-ABCD", mockContext), "Letters should fail.");
        assertFalse(validator.isValid("EMP- 123", mockContext), "Whitespace inside code should fail.");
    }
}
