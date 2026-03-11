package com.example.twiliosmssender.repository;

import com.example.twiliosmssender.domain.SmsMessage;
import com.example.twiliosmssender.domain.SmsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SmsMessage} entities.
 *
 * <p>Spring Data automatically generates the implementation of this interface
 * at runtime. You do not need to write any SQL or JPQL queries for the
 * methods declared here — Spring derives them from the method name conventions.
 *
 * <p>The {@code JpaRepository<SmsMessage, Long>} base interface provides
 * built-in CRUD operations: {@code save()}, {@code findById()}, {@code findAll()},
 * {@code deleteById()}, etc.
 *
 * <h2>Custom query methods</h2>
 * <ul>
 *   <li>{@link #findByTwilioSid(String)} – look up a message by its Twilio SID</li>
 *   <li>{@link #findByStatus(SmsStatus)} – filter messages by delivery status</li>
 *   <li>{@link #findByToNumber(String)} – find all messages sent to a specific number</li>
 *   <li>{@link #existsByTwilioSid(String)} – check if a Twilio SID already exists</li>
 * </ul>
 */
@Repository
public interface SmsMessageRepository extends JpaRepository<SmsMessage, Long> {

    /**
     * Finds an SMS message record by its Twilio Message SID.
     *
     * <p>The Twilio SID is a globally unique identifier assigned by Twilio
     * (format: "SM" + 32 hex characters). This method is useful for correlating
     * a webhook callback from Twilio back to a local record.
     *
     * @param twilioSid the Twilio-assigned message SID
     * @return an {@link Optional} containing the record, or empty if not found
     */
    Optional<SmsMessage> findByTwilioSid(String twilioSid);

    /**
     * Returns all SMS messages with the given delivery status.
     *
     * <p>Useful for monitoring dashboards: e.g., find all FAILED messages to
     * investigate delivery issues.
     *
     * @param status the delivery status to filter by
     * @return a list of matching messages; empty list if none exist
     */
    List<SmsMessage> findByStatus(SmsStatus status);

    /**
     * Returns all SMS messages sent to a specific phone number.
     *
     * <p>Useful for compliance lookups: "What messages have we sent to +15551234567?"
     *
     * @param toNumber the recipient phone number in E.164 format
     * @return a list of messages sent to that number; empty list if none
     */
    List<SmsMessage> findByToNumber(String toNumber);

    /**
     * Checks whether a record with the given Twilio SID already exists.
     *
     * <p>Used for idempotency checks — prevents duplicate records if the same
     * Twilio SID is received more than once (e.g., from a webhook retry).
     *
     * @param twilioSid the Twilio-assigned message SID
     * @return {@code true} if a record with this SID exists, {@code false} otherwise
     */
    boolean existsByTwilioSid(String twilioSid);
}
