package com.example.customvalidator.validator;

import com.example.customvalidator.annotation.ValidEmployeeCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Implementation of the logical validation for the @ValidEmployeeCode
 * annotation.
 * It checks if a string begins with 'EMP-' and has exactly 4 digits.
 */
public class EmployeeCodeValidator implements ConstraintValidator<ValidEmployeeCode, String> {

    // Regex pattern for validating string: starts with 'EMP-' and followed by
    // exactly 4 digits.
    private static final Pattern PATTERN = Pattern.compile("^EMP-\\d{4}$");

    /**
     * Initialization method. In some cases, we might want to capture parameters
     * from the annotation to configure the validation. Here, we just log or ignore
     * it.
     */
    @Override
    public void initialize(ValidEmployeeCode constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    /**
     * The core validation logic.
     * 
     * @param value   The object to validate
     * @param context Context in which the constraint is evaluated
     * @return true if the validation passes, false otherwise
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // If the value is null, we can return true here and let @NotNull handle null
        // checks separately.
        // It's a common practice. If we want it to implicitly reject nulls, return
        // false.
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        // Return true if the value matches the pattern, otherwise false.
        return PATTERN.matcher(value).matches();
    }
}
