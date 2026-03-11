package com.example.twiliosmssender.domain;

/**
 * Enum representing the lifecycle status of an SMS message.
 *
 * <p>This maps the subset of Twilio message statuses that are relevant for
 * our local audit records. Twilio has a richer set of statuses (e.g., "accepted",
 * "scheduled", "read") but we simplify to the most common outcomes.
 *
 * <p>Twilio status → local SmsStatus mapping:
 * <ul>
 *   <li>{@code queued}      → {@link #QUEUED}   – accepted by Twilio, not yet sent</li>
 *   <li>{@code sending}     → {@link #SENDING}  – in transit to the carrier</li>
 *   <li>{@code sent}        → {@link #SENT}     – carrier accepted the message</li>
 *   <li>{@code delivered}   → {@link #DELIVERED} – delivery confirmed by carrier</li>
 *   <li>{@code failed}      → {@link #FAILED}   – message could not be delivered</li>
 *   <li>{@code undelivered} → {@link #UNDELIVERED} – carrier returned an error</li>
 *   <li>any other status    → {@link #QUEUED}   – safe default</li>
 * </ul>
 *
 * @see <a href="https://www.twilio.com/docs/sms/api/message-resource#message-status-values">
 *      Twilio Message Status Values</a>
 */
public enum SmsStatus {

    /**
     * The message has been accepted by Twilio's API and is waiting to be sent.
     * This is the initial status right after a successful API call.
     */
    QUEUED,

    /**
     * Twilio is currently transmitting the message to the carrier network.
     */
    SENDING,

    /**
     * The carrier network has accepted the message for delivery.
     * Note: "sent" does NOT guarantee the recipient received it.
     */
    SENT,

    /**
     * The carrier has confirmed the message was delivered to the recipient's device.
     * Not all carriers support delivery receipts.
     */
    DELIVERED,

    /**
     * Twilio was unable to send the message (e.g., invalid number, account issue).
     */
    FAILED,

    /**
     * The carrier accepted the message but could not deliver it
     * (e.g., the recipient's phone is off or the number is invalid).
     */
    UNDELIVERED
}
