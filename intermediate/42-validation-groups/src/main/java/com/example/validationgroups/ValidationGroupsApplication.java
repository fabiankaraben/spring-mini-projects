package com.example.validationgroups;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Validation Groups mini-project.
 *
 * <h2>What this project demonstrates</h2>
 * <p>Bean Validation Groups allow you to apply <em>different sets of constraints</em>
 * depending on the context in which an object is validated.  The classic use-case is a
 * REST API where:</p>
 * <ul>
 *   <li><strong>Create (POST)</strong> – all mandatory fields are required (name, email,
 *       password, role).</li>
 *   <li><strong>Update (PATCH)</strong> – only the fields that are editable can be changed
 *       (name, email); the password and role are intentionally excluded from the update
 *       group so they cannot be changed through the normal update endpoint.</li>
 *   <li><strong>Password Change (PUT /change-password)</strong> – only the password fields
 *       are validated; all other fields are ignored.</li>
 * </ul>
 *
 * <h2>Key Spring / Jakarta Validation concepts used</h2>
 * <ul>
 *   <li>{@code @Validated(SomeGroup.class)} on the controller method – activates the
 *       group-specific constraint set for the incoming request body.</li>
 *   <li>Marker interfaces ({@code OnCreate}, {@code OnUpdate}, {@code OnPasswordChange})
 *       as group identifiers.</li>
 *   <li>Constraint annotations that target one or more groups, e.g.
 *       {@code @NotBlank(groups = OnCreate.class)}.</li>
 *   <li>Ordered validation with {@code @GroupSequence} to enforce cross-group ordering.</li>
 * </ul>
 */
@SpringBootApplication
public class ValidationGroupsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ValidationGroupsApplication.class, args);
    }
}
