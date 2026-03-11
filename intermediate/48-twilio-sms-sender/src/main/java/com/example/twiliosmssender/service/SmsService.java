package com.example.twiliosmssender.service;

import com.example.twiliosmssender.domain.SmsMessage;
import com.example.twiliosmssender.domain.SmsStatus;
import com.example.twiliosmssender.dto.SendSmsRequest;
import com.example.twiliosmssender.dto.SmsResponse;
import com.example.twiliosmssender.exception.SmsNotFoundException;
import com.example.twiliosmssender.exception.TwilioSmsException;
import com.example.twiliosmssender.repository.SmsMessageRepository;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for sending SMS messages via the Twilio REST API and
 * persisting message records to PostgreSQL.
 *
 * <h2>Architecture overview</h2>
 * <pre>
 *   REST Controller  →  SmsService  →  Twilio Java SDK  →  Twilio REST API
 *                              ↓
 *                       SmsMessageRepository  →  PostgreSQL
 * </pre>
 *
 * <h2>Twilio SDK usage pattern</h2>
 * <p>The Twilio Java SDK uses a fluent builder / creator pattern:
 * <ol>
 *   <li>Create a {@link MessageCreator} via {@code Message.creator(to, from, body)}.</li>
 *   <li>Optionally chain configuration methods on the creator.</li>
 *   <li>Call {@code .create()} to perform the actual HTTP request to Twilio's API.</li>
 *   <li>The returned {@link Message} object contains the SID, status, and error info.</li>
 * </ol>
 *
 * <p>The SDK throws {@link ApiException} (unchecked) if the HTTP request fails or
 * if Twilio returns an error code. We catch this and re-throw as {@link TwilioSmsException}.
 *
 * <h2>Testability</h2>
 * <p>The Twilio SDK uses static factory methods ({@code Message.creator(...)}). To keep
 * the service testable without making actual Twilio API calls in unit tests, we provide a
 * package-private {@link #createTwilioMessage(PhoneNumber, PhoneNumber, String)} method
 * that unit tests can spy/override.
 *
 * <p>Integration tests use WireMock to intercept the real HTTP calls made by the Twilio SDK.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    /** Repository for persisting and querying local SMS records. */
    private final SmsMessageRepository smsMessageRepository;

    /**
     * The Twilio phone number used as the SMS sender ("from" number).
     * Injected from {@code twilio.phone-number} in application.yml.
     * Must be a Twilio-provisioned number in E.164 format.
     */
    @Value("${twilio.phone-number}")
    private String fromPhoneNumber;

    /**
     * Constructor injection — preferred over field injection for testability.
     *
     * @param smsMessageRepository the repository for SMS records
     */
    public SmsService(SmsMessageRepository smsMessageRepository) {
        this.smsMessageRepository = smsMessageRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send SMS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends an SMS message via the Twilio API and persists a local audit record.
     *
     * <h3>Steps performed:</h3>
     * <ol>
     *   <li>Create a local {@link SmsMessage} record with status {@code QUEUED} and save it.
     *       (We save before calling Twilio so that even if the app crashes mid-flight,
     *        we have a record of the attempt.)</li>
     *   <li>Call the Twilio API via the Java SDK.</li>
     *   <li>Update the record with the Twilio SID and status returned by Twilio.</li>
     *   <li>If Twilio throws an error, update the record with FAILED status and error details.</li>
     * </ol>
     *
     * @param request the validated DTO containing the recipient number and message body
     * @return a {@link SmsResponse} DTO with the Twilio SID and current status
     * @throws TwilioSmsException if the Twilio API call fails
     */
    @Transactional
    public SmsResponse sendSms(SendSmsRequest request) {
        log.info("Sending SMS to: {}", request.to());

        // Step 1: Create and save a local record with QUEUED status.
        // Persisting before the API call means we always have an audit trail.
        SmsMessage smsMessage = new SmsMessage(
                request.to(),
                fromPhoneNumber,
                request.body(),
                SmsStatus.QUEUED
        );
        SmsMessage saved = smsMessageRepository.save(smsMessage);
        log.debug("Created SMS record with local id={}", saved.getId());

        try {
            // Step 2: Call the Twilio API to send the message.
            // PhoneNumber is Twilio's value-type wrapper for E.164 phone numbers.
            Message twilioMessage = createTwilioMessage(
                    new PhoneNumber(request.to()),
                    new PhoneNumber(fromPhoneNumber),
                    request.body()
            );

            log.info("Twilio API call successful: sid={}, status={}",
                    twilioMessage.getSid(), twilioMessage.getStatus());

            // Step 3: Update the local record with the Twilio SID and status.
            saved.setTwilioSid(twilioMessage.getSid());
            saved.setStatus(mapTwilioStatus(twilioMessage.getStatus().toString()));
            SmsMessage updated = smsMessageRepository.save(saved);

            return SmsResponse.from(updated);

        } catch (ApiException e) {
            // Step 4a: On Twilio API failure, update the record to FAILED.
            log.error("Twilio API error for local id={}: code={}, message={}",
                    saved.getId(), e.getCode(), e.getMessage());

            saved.setStatus(SmsStatus.FAILED);
            saved.setErrorCode(e.getCode());
            saved.setErrorMessage(e.getMessage());
            smsMessageRepository.save(saved);

            // Wrap the Twilio SDK exception in our domain exception
            throw new TwilioSmsException("Failed to send SMS via Twilio: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // List all messages
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all SMS message records from the local database.
     *
     * <p>This reads only from the local database — it does NOT call the Twilio API.
     *
     * @return a list of all {@link SmsResponse} DTOs; empty list if no records exist
     */
    @Transactional(readOnly = true)
    public List<SmsResponse> listMessages() {
        return smsMessageRepository.findAll().stream()
                .map(SmsResponse::from)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get by local ID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single SMS message record by its local database ID.
     *
     * @param id the local PostgreSQL primary key
     * @return the {@link SmsResponse} DTO for that record
     * @throws SmsNotFoundException if no record exists with the given ID
     */
    @Transactional(readOnly = true)
    public SmsResponse getMessageById(Long id) {
        SmsMessage message = smsMessageRepository.findById(id)
                .orElseThrow(() -> new SmsNotFoundException(id));
        return SmsResponse.from(message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get by Twilio SID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single SMS message record by its Twilio Message SID.
     *
     * <p>Useful for looking up a message after receiving a Twilio status webhook.
     *
     * @param twilioSid the Twilio-assigned message SID (e.g., {@code SMxxxxxxxx...})
     * @return the {@link SmsResponse} DTO for that record
     * @throws SmsNotFoundException if no record exists with the given Twilio SID
     */
    @Transactional(readOnly = true)
    public SmsResponse getMessageByTwilioSid(String twilioSid) {
        SmsMessage message = smsMessageRepository.findByTwilioSid(twilioSid)
                .orElseThrow(() -> new SmsNotFoundException(twilioSid));
        return SmsResponse.from(message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get by status
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all SMS records with the given delivery status.
     *
     * @param status the {@link SmsStatus} to filter by
     * @return a list of matching {@link SmsResponse} DTOs; empty list if none found
     */
    @Transactional(readOnly = true)
    public List<SmsResponse> getMessagesByStatus(SmsStatus status) {
        return smsMessageRepository.findByStatus(status).stream()
                .map(SmsResponse::from)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Twilio SDK call (package-private for testability)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wraps the static Twilio SDK call to create and send an SMS message.
     *
     * <p>This method is intentionally package-private (not private) so that unit
     * tests using Mockito can spy on this service and override just this method,
     * avoiding real Twilio HTTP calls without needing to mock the entire SDK.
     *
     * <p>The Twilio SDK fluent API used here:
     * <pre>{@code
     * Message.creator(to, from, body)  // builds a MessageCreator
     *        .create()                  // sends the HTTP POST to Twilio
     * }</pre>
     *
     * @param to   the recipient phone number as a Twilio {@link PhoneNumber}
     * @param from the sender (Twilio) phone number as a Twilio {@link PhoneNumber}
     * @param body the SMS text body
     * @return the {@link Message} object returned by Twilio containing SID and status
     * @throws ApiException if Twilio returns an error
     */
    Message createTwilioMessage(PhoneNumber to, PhoneNumber from, String body) {
        // Message.creator(...).create() performs the actual HTTP POST to Twilio's API.
        // The Twilio SDK uses the credentials initialized in TwilioConfig#initTwilio().
        return Message.creator(to, from, body).create();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status mapping (package-private for unit testing)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Maps a Twilio message status string to our local {@link SmsStatus} enum.
     *
     * <p>Twilio status values: "queued", "sending", "sent", "delivered",
     * "failed", "undelivered", "accepted", "receiving", "received",
     * "read", "canceled". We map only the most common ones.
     *
     * @param twilioStatus the status string from the Twilio API response
     * @return the corresponding {@link SmsStatus}, defaulting to {@link SmsStatus#QUEUED}
     *         for any unrecognised status string
     */
    SmsStatus mapTwilioStatus(String twilioStatus) {
        if (twilioStatus == null) return SmsStatus.QUEUED;
        return switch (twilioStatus.toLowerCase()) {
            case "queued"      -> SmsStatus.QUEUED;
            case "sending"     -> SmsStatus.SENDING;
            case "sent"        -> SmsStatus.SENT;
            case "delivered"   -> SmsStatus.DELIVERED;
            case "failed"      -> SmsStatus.FAILED;
            case "undelivered" -> SmsStatus.UNDELIVERED;
            // Any future Twilio status not yet mapped defaults to QUEUED (safe default)
            default            -> SmsStatus.QUEUED;
        };
    }
}
