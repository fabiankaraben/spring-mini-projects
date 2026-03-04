package com.example.customvalidator.annotation;

import com.example.customvalidator.validator.EmployeeCodeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom Bean Validation annotation for validating an Employee Code.
 * The code must follow the pattern "EMP-" followed by exactly 4 digits.
 */
@Documented
@Constraint(validatedBy = EmployeeCodeValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmployeeCode {

    /**
     * Default error message when validation fails.
     */
    String message() default "Invalid employee code format. It must start with 'EMP-' followed by 4 digits (e.g., EMP-1234).";

    /**
     * Groups to allow restricting validation to specific use cases.
     */
    Class<?>[] groups() default {};

    /**
     * Payloads to associate additional metadata with a constraint declaration.
     */
    Class<? extends Payload>[] payload() default {};
}
