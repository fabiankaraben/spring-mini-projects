package com.example.javamailsender.dto;

/**
 * Response DTO returned by the email endpoints after a send attempt.
 *
 * <p>Provides a human-readable status message and the recipient address so
 * the caller can confirm which address was targeted.
 *
 * @param status  A short status string, either {@code "sent"} on success or
 *                {@code "failed"} when an exception occurred.
 * @param to      The recipient email address that was used in the send attempt.
 * @param message A human-readable description of the outcome.
 */
public record EmailResponse(

        /**
         * Short status of the email send operation.
         * Possible values: {@code "sent"}, {@code "failed"}.
         */
        String status,

        /**
         * The recipient email address supplied in the request.
         */
        String to,

        /**
         * A human-readable description of the result.
         * On success: "Email sent successfully."
         * On failure: a description of the error.
         */
        String message
) {
}
