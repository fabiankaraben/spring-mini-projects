package com.example.validationgroups.dto;

import com.example.validationgroups.validation.OnCreate;
import com.example.validationgroups.validation.OnPasswordChange;
import com.example.validationgroups.validation.OnUpdate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Unified request DTO for all user-related write operations.
 *
 * <h2>Core concept – Validation Groups</h2>
 * <p>Each field carries constraint annotations that declare which
 * <em>validation group</em> activates them.  The controller method
 * selects a group via {@code @Validated(SomeGroup.class)}, so the same
 * DTO class is safely reused across CREATE, UPDATE, and CHANGE-PASSWORD
 * endpoints without creating three separate DTO classes.</p>
 *
 * <h2>Field activation matrix</h2>
 * <pre>
 * Field           | OnCreate | OnUpdate | OnPasswordChange
 * ----------------|----------|----------|-----------------
 * name            |    ✓     |    ✓     |        ✗
 * email           |    ✓     |    ✓     |        ✗
 * password        |    ✓     |    ✗     |        ✗
 * role            |    ✓     |    ✗     |        ✗
 * newPassword     |    ✗     |    ✗     |        ✓
 * confirmPassword |    ✗     |    ✗     |        ✓
 * </pre>
 *
 * <p>Fields with no constraint annotation for the active group are
 * simply ignored by the validator – they may be {@code null} in the
 * incoming JSON without causing a validation error.</p>
 *
 * @param name            Display name.  Required on create/update; ignored on password-change.
 * @param email           Email address.  Required and must be valid on create/update.
 * @param password        Initial password.  Required on create only; use newPassword to change it.
 * @param role            User role (e.g. "USER", "ADMIN").  Required on create; not editable on update.
 * @param newPassword     New password.  Required only when changing the password.
 * @param confirmPassword Must match {@code newPassword}.  Required only on password-change.
 */
public record UserRequest(

        /**
         * User's display name.
         *
         * <ul>
         *   <li>{@code @NotBlank} – activated for CREATE and UPDATE, rejected if blank.</li>
         *   <li>{@code @Size} – 2–100 characters, same groups.</li>
         * </ul>
         * Not validated at all during a password-change operation.
         */
        @NotBlank(groups = {OnCreate.class, OnUpdate.class},
                  message = "Name must not be blank")
        @Size(min = 2, max = 100, groups = {OnCreate.class, OnUpdate.class},
              message = "Name must be between 2 and 100 characters")
        String name,

        /**
         * User's email address.
         *
         * <ul>
         *   <li>{@code @NotBlank} – required on CREATE and UPDATE.</li>
         *   <li>{@code @Email} – must be a syntactically valid email address.
         *       Activated for CREATE and UPDATE.</li>
         * </ul>
         */
        @NotBlank(groups = {OnCreate.class, OnUpdate.class},
                  message = "Email must not be blank")
        @Email(groups = {OnCreate.class, OnUpdate.class},
               message = "Email must be a valid email address")
        String email,

        /**
         * Initial password supplied during account creation.
         *
         * <ul>
         *   <li>{@code @NotBlank} – required only for the {@code OnCreate} group.</li>
         *   <li>{@code @Size} – minimum 8 characters, only for CREATE.</li>
         * </ul>
         * Ignored on UPDATE and password-change operations (use {@code newPassword} instead).
         */
        @NotBlank(groups = OnCreate.class,
                  message = "Password is required when creating a user")
        @Size(min = 8, groups = OnCreate.class,
              message = "Password must be at least 8 characters")
        String password,

        /**
         * User role identifier (e.g. "USER", "ADMIN", "MODERATOR").
         *
         * <ul>
         *   <li>{@code @NotBlank} – required only on CREATE.</li>
         *   <li>{@code @Pattern} – only "USER", "ADMIN", or "MODERATOR" are accepted.</li>
         * </ul>
         * Not editable through the standard UPDATE endpoint; no {@code OnUpdate} constraints here.
         */
        @NotBlank(groups = OnCreate.class,
                  message = "Role is required when creating a user")
        @Pattern(regexp = "USER|ADMIN|MODERATOR", groups = OnCreate.class,
                 message = "Role must be one of: USER, ADMIN, MODERATOR")
        String role,

        /**
         * New password for the dedicated change-password operation.
         *
         * <ul>
         *   <li>{@code @NotBlank} – required only when the {@code OnPasswordChange} group is active.</li>
         *   <li>{@code @Size} – minimum 8 characters, only for password-change.</li>
         * </ul>
         * Ignored during CREATE and UPDATE operations.
         */
        @NotBlank(groups = OnPasswordChange.class,
                  message = "New password is required")
        @Size(min = 8, groups = OnPasswordChange.class,
              message = "New password must be at least 8 characters")
        String newPassword,

        /**
         * Confirmation of the new password – must match {@code newPassword}.
         *
         * <p>The cross-field equality check (newPassword == confirmPassword) is enforced
         * in the service layer rather than via a custom constraint for simplicity.</p>
         *
         * Only validated when the {@code OnPasswordChange} group is active.
         */
        @NotBlank(groups = OnPasswordChange.class,
                  message = "Password confirmation is required")
        String confirmPassword
) {}
