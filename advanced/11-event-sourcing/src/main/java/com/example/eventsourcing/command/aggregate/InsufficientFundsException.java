package com.example.eventsourcing.command.aggregate;

/**
 * Exception thrown by {@link BankAccountAggregate} when a withdrawal is requested
 * for an amount greater than the current account balance.
 *
 * <p>This is a domain exception — it represents a violation of the business rule
 * "no overdraft allowed". It extends {@link RuntimeException} so it propagates
 * through Axon's command bus and reaches the REST controller's exception handler.
 */
public class InsufficientFundsException extends RuntimeException {

    /**
     * Constructs a new exception with the given detail message.
     *
     * @param message a human-readable description of the insufficient-funds condition
     */
    public InsufficientFundsException(String message) {
        super(message);
    }
}
