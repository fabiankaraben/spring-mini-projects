package com.example.stripepayment.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that initializes the Stripe Java SDK.
 *
 * <p>The Stripe SDK uses a global static API key (set via {@link Stripe#apiKey}).
 * This class reads the key from {@code application.yml} and sets it once
 * during Spring context startup via {@link PostConstruct}.
 *
 * <p>Configuration property: {@code stripe.api-key}
 *
 * <p>For local development and testing, Stripe provides test-mode API keys
 * prefixed with {@code sk_test_}. These keys can be obtained for free from
 * the Stripe Dashboard and do not charge real money.
 *
 * <p>In production, use the live API key ({@code sk_live_...}) and inject it
 * from a secrets manager (e.g., AWS Secrets Manager, HashiCorp Vault) rather
 * than hardcoding it.
 *
 * <p>For integration tests, we use Stripe's test-mode key and optionally
 * override the base URL to point at a local mock (see test configuration).
 */
@Configuration
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    /**
     * The Stripe secret API key, injected from application properties.
     *
     * <p>Value comes from {@code application.yml}:
     * <pre>
     * stripe:
     *   api-key: ${STRIPE_API_KEY:sk_test_placeholder}
     * </pre>
     *
     * <p>In Docker Compose, inject via the {@code STRIPE_API_KEY} environment variable.
     */
    @Value("${stripe.api-key}")
    private String stripeApiKey;

    /**
     * Sets the global Stripe API key on application startup.
     *
     * <p>The Stripe Java SDK stores the API key globally in {@link Stripe#apiKey}.
     * All subsequent API calls ({@code PaymentIntent.create()}, etc.) will use
     * this key unless overridden per-request.
     */
    @PostConstruct
    public void configureStripe() {
        Stripe.apiKey = stripeApiKey;
        log.info("Stripe SDK initialized. Using key prefix: {}",
                stripeApiKey.length() > 10 ? stripeApiKey.substring(0, 10) + "..." : "[short key]");
    }
}
