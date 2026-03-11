package com.example.twiliosmssender.dto;

import com.example.twiliosmssender.domain.SmsMessage;
import com.example.twiliosmssender.domain.SmsStatus;

import java.time.Instant;

/**
 * DTO (Data Transfer Object) for the SMS message HTTP response body.
 *
 * <p>Returned by all endpoints that produce SMS message data. It converts
 * the {@link SmsMessage} JPA entity into a plain record safe to serialize
 * directly to JSON via Jackson.
 *
 * <p>Example JSON response:
 * <pre>{@code
 * {
 *   "id": 1,
 *   "twilioSid": "SMxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
 *   "toNumber": "+15551234567",
 *   "fromNumber": "+15559876543",
 *   "body": "Hello from Spring Boot + Twilio!",
 *   "status": "QUEUED",
 *   "errorCode": null,
 *   "errorMessage": null,
 *   "createdAt": "2024-01-15T10:30:00Z",
 *   "updatedAt": "2024-01-15T10:30:00Z"
 * }
 * }</pre>
 *
 * @param id           the local database ID
 * @param twilioSid    the Twilio-assigned message SID (may be null if sending failed)
 * @param toNumber     the recipient phone number in E.164 format
 * @param fromNumber   the sender (Twilio) phone number in E.164 format
 * @param body         the text content of the SMS
 * @param status       the current delivery status
 * @param errorCode    optional Twilio error code (non-null only when status is FAILED)
 * @param errorMessage optional human-readable error description from Twilio
 * @param createdAt    timestamp when the record was created
 * @param updatedAt    timestamp of the last status update
 */
public record SmsResponse(
        Long id,
        String twilioSid,
        String toNumber,
        String fromNumber,
        String body,
        SmsStatus status,
        Integer errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Factory method that maps a {@link SmsMessage} entity to a {@link SmsResponse} DTO.
     *
     * <p>Keeping the conversion logic here — rather than scattering it across service
     * and controller layers — ensures there is a single place to update if the DTO
     * shape changes.
     *
     * @param message the JPA entity to convert
     * @return a new SmsResponse DTO populated from the entity fields
     */
    public static SmsResponse from(SmsMessage message) {
        return new SmsResponse(
                message.getId(),
                message.getTwilioSid(),
                message.getToNumber(),
                message.getFromNumber(),
                message.getBody(),
                message.getStatus(),
                message.getErrorCode(),
                message.getErrorMessage(),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}
