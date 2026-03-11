package com.example.twiliosmssender.config;

import com.twilio.Twilio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Spring configuration that initializes the Twilio SDK at application startup.
 *
 * <p>The Twilio Java SDK uses a static initializer pattern: you call
 * {@link Twilio#init(String, String)} once with your Account SID and Auth Token,
 * and from that point forward any Twilio resource class (e.g., {@code Message.creator()})
 * uses those credentials automatically.
 *
 * <p>Credentials are injected from application properties so they can be
 * overridden via environment variables without changing the source code:
 * <ul>
 *   <li>{@code TWILIO_ACCOUNT_SID} overrides {@code twilio.account-sid}</li>
 *   <li>{@code TWILIO_AUTH_TOKEN}  overrides {@code twilio.auth-token}</li>
 * </ul>
 *
 * <p><strong>Security note:</strong> Never commit real credentials to version control.
 * Use environment variables or a secrets manager in production.
 */
@Configuration
public class TwilioConfig {

    private static final Logger log = LoggerFactory.getLogger(TwilioConfig.class);

    /**
     * Twilio Account SID – the unique identifier for your Twilio account.
     * Starts with "AC" followed by 32 hex characters.
     * Injected from {@code twilio.account-sid} (application.yml).
     */
    @Value("${twilio.account-sid}")
    private String accountSid;

    /**
     * Twilio Auth Token – secret credential paired with the Account SID.
     * Injected from {@code twilio.auth-token} (application.yml).
     */
    @Value("${twilio.auth-token}")
    private String authToken;

    /**
     * Initializes the Twilio SDK after the Spring bean is constructed and
     * all {@code @Value} fields have been injected.
     *
     * <p>{@code @PostConstruct} guarantees this runs before any service
     * that depends on the SDK being initialized.
     */
    @PostConstruct
    public void initTwilio() {
        // Initialize the Twilio static client with the account credentials.
        // After this call, all Twilio resource classes (Message, Call, etc.)
        // will use these credentials for API requests.
        Twilio.init(accountSid, authToken);
        log.info("Twilio SDK initialized for account SID: {}...{}",
                accountSid.substring(0, Math.min(8, accountSid.length())),
                accountSid.length() > 8 ? accountSid.substring(accountSid.length() - 4) : "");
    }
}
