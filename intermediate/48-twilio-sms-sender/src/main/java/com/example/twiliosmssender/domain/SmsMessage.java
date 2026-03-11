package com.example.twiliosmssender.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity that represents a single SMS message record in the local database.
 *
 * <p>Every time the application sends (or attempts to send) an SMS via Twilio,
 * a record is created here for auditing and tracking purposes. This allows
 * querying the history of sent messages without calling the Twilio API.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code id}          – auto-generated primary key (PostgreSQL SERIAL)</li>
 *   <li>{@code twilioSid}   – the unique message SID returned by Twilio (e.g., {@code SM...})</li>
 *   <li>{@code toNumber}    – the recipient's phone number in E.164 format (e.g., +15551234567)</li>
 *   <li>{@code fromNumber}  – the Twilio sender phone number in E.164 format</li>
 *   <li>{@code body}        – the text content of the SMS (max 1600 chars)</li>
 *   <li>{@code status}      – the delivery status (see {@link SmsStatus})</li>
 *   <li>{@code errorCode}   – optional Twilio error code when sending fails</li>
 *   <li>{@code errorMessage}– optional human-readable error description from Twilio</li>
 *   <li>{@code createdAt}   – timestamp when the record was created (set by {@code @PrePersist})</li>
 *   <li>{@code updatedAt}   – timestamp of the last status change (set by {@code @PreUpdate})</li>
 * </ul>
 *
 * @see SmsStatus
 */
@Entity
@Table(name = "sms_messages")
public class SmsMessage {

    // ─────────────────────────────────────────────────────────────────────────
    // Primary key
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Auto-generated surrogate primary key.
     * The database generates the value using a sequence (IDENTITY strategy).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─────────────────────────────────────────────────────────────────────────
    // Twilio fields
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The Twilio Message SID – a globally unique identifier assigned by Twilio.
     * Format: "SM" followed by 32 hex characters (e.g., "SMxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx").
     * Nullable because the SID is only available after a successful Twilio API call.
     */
    @Column(name = "twilio_sid", unique = true)
    private String twilioSid;

    /**
     * The recipient's phone number in E.164 format (e.g., "+15551234567").
     * E.164 format: "+" followed by country code and subscriber number, no spaces or dashes.
     */
    @Column(name = "to_number", nullable = false, length = 20)
    private String toNumber;

    /**
     * The Twilio sender phone number in E.164 format.
     * This is the "from" number purchased from or provided by Twilio.
     */
    @Column(name = "from_number", nullable = false, length = 20)
    private String fromNumber;

    /**
     * The text content of the SMS message.
     * Standard SMS messages are limited to 160 characters; longer messages are
     * split into segments. Twilio concatenates them for delivery (up to 1600 chars).
     */
    @Column(name = "body", nullable = false, length = 1600)
    private String body;

    // ─────────────────────────────────────────────────────────────────────────
    // Status and error tracking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Current delivery status of this message.
     * Stored as a string in the database using {@code @Enumerated(EnumType.STRING)}
     * so the value is human-readable (e.g., "QUEUED") rather than a numeric ordinal.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SmsStatus status;

    /**
     * Optional Twilio error code returned when a message fails to send.
     * Twilio error codes are documented at: https://www.twilio.com/docs/api/errors
     */
    @Column(name = "error_code")
    private Integer errorCode;

    /**
     * Optional human-readable error message from Twilio explaining why the send failed.
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    // ─────────────────────────────────────────────────────────────────────────
    // Timestamps
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The timestamp when this record was first created.
     * Set automatically by the {@link #prePersist()} lifecycle callback.
     * Uses {@code Instant} (UTC epoch-based) for timezone-neutral storage.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * The timestamp of the last status update.
     * Set by {@link #prePersist()} on creation and updated by {@link #preUpdate()}.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ─────────────────────────────────────────────────────────────────────────
    // JPA lifecycle callbacks
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by JPA before the entity is first persisted (INSERT).
     * Sets both {@link #createdAt} and {@link #updatedAt} to the current UTC time.
     */
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Called by JPA before each UPDATE operation.
     * Keeps {@link #updatedAt} in sync with the actual update time.
     */
    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Default no-argument constructor required by JPA.
     * Do not use directly in application code — use the parameterized constructor instead.
     */
    protected SmsMessage() {
    }

    /**
     * Creates a new SMS message record with the minimum required fields.
     *
     * @param toNumber   the recipient phone number in E.164 format
     * @param fromNumber the sender (Twilio) phone number in E.164 format
     * @param body       the text content of the message
     * @param status     the initial delivery status
     */
    public SmsMessage(String toNumber, String fromNumber, String body, SmsStatus status) {
        this.toNumber = toNumber;
        this.fromNumber = fromNumber;
        this.body = body;
        this.status = status;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters and setters
    // ─────────────────────────────────────────────────────────────────────────

    /** @return the auto-generated local database ID */
    public Long getId() { return id; }

    /** @return the Twilio Message SID (may be null for failed pre-submission messages) */
    public String getTwilioSid() { return twilioSid; }

    /** @param twilioSid the Twilio-assigned message SID */
    public void setTwilioSid(String twilioSid) { this.twilioSid = twilioSid; }

    /** @return the recipient phone number in E.164 format */
    public String getToNumber() { return toNumber; }

    /** @return the sender (Twilio) phone number in E.164 format */
    public String getFromNumber() { return fromNumber; }

    /** @return the text body of the SMS */
    public String getBody() { return body; }

    /** @return the current delivery status */
    public SmsStatus getStatus() { return status; }

    /** @param status the new delivery status */
    public void setStatus(SmsStatus status) { this.status = status; }

    /** @return the Twilio error code, or null if no error */
    public Integer getErrorCode() { return errorCode; }

    /** @param errorCode the Twilio error code */
    public void setErrorCode(Integer errorCode) { this.errorCode = errorCode; }

    /** @return the Twilio error message, or null if no error */
    public String getErrorMessage() { return errorMessage; }

    /** @param errorMessage the Twilio error message */
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    /** @return the timestamp when this record was first persisted */
    public Instant getCreatedAt() { return createdAt; }

    /** @return the timestamp of the last status update */
    public Instant getUpdatedAt() { return updatedAt; }
}
