package com.example.eventsourcing.command.api;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to close an existing bank account.
 *
 * <p>Business rules enforced by the aggregate:
 * <ul>
 *   <li>An already-closed account cannot be closed again.</li>
 *   <li>An account with a non-zero balance requires the caller to acknowledge
 *       the remaining balance will be forfeited (or the business may require
 *       a zero balance before closure — demonstrated here with a configurable rule).</li>
 * </ul>
 *
 * @param accountId the account to close
 * @param reason    optional free-text reason for closure
 */
public record CloseAccountCommand(

        @TargetAggregateIdentifier
        String accountId,

        String reason
) {}
