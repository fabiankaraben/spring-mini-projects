package com.example.validationgroups.validation;

import jakarta.validation.GroupSequence;
import jakarta.validation.groups.Default;

/**
 * Ordered validation sequence for the {@code OnCreate} group.
 *
 * <h2>Why group sequences?</h2>
 * <p>By default, Bean Validation runs all constraint groups in an <em>undefined</em>
 * order and reports every violation found.  A {@link GroupSequence} lets you declare
 * a deterministic order: the next group is only evaluated if the previous group
 * passed without any violations.</p>
 *
 * <h2>How this sequence works</h2>
 * <ol>
 *   <li><strong>{@link Default}</strong> – standard constraints (e.g.
 *       {@code @Email}, {@code @Size}) are checked first.</li>
 *   <li><strong>{@link OnCreate}</strong> – create-specific constraints (e.g.
 *       mandatory password) are only checked if the Default group passed.
 *       This avoids flooding the client with redundant errors when the most
 *       basic constraints already fail.</li>
 * </ol>
 *
 * <h2>Controller usage</h2>
 * <pre>{@code
 * @PostMapping
 * public ResponseEntity<UserResponse> create(
 *         @RequestBody @Validated(UserValidationSequence.class) UserRequest request) { ... }
 * }</pre>
 *
 * <p>Using the sequence instead of bare {@code OnCreate.class} ensures that
 * {@code Default}-group constraints (like {@code @Email}) are validated first,
 * and if they fail, the {@code OnCreate}-group constraints are skipped.  This
 * produces cleaner, less confusing error messages for the API consumer.</p>
 */
@GroupSequence({Default.class, OnCreate.class})
public interface UserValidationSequence {
    // Marker interface that defines a validation order: Default → OnCreate.
    // No methods are needed – the annotation carries all the information.
}
