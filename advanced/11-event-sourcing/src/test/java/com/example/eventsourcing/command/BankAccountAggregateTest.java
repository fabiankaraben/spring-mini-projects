package com.example.eventsourcing.command;

import com.example.eventsourcing.command.aggregate.AccountStatus;
import com.example.eventsourcing.command.aggregate.BankAccountAggregate;
import com.example.eventsourcing.command.aggregate.InsufficientFundsException;
import com.example.eventsourcing.command.api.*;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Unit tests for {@link BankAccountAggregate} using Axon's {@link AggregateTestFixture}.
 *
 * <h2>Testing strategy</h2>
 * These tests verify domain logic in complete isolation:
 * <ul>
 *   <li>No Spring context is loaded (millisecond startup)</li>
 *   <li>No database is needed — the fixture uses an in-memory event store</li>
 *   <li>Given-when-then syntax mirrors Event Sourcing concepts naturally</li>
 * </ul>
 *
 * <h2>Given-when-then pattern</h2>
 * <pre>
 *   fixture.given(past events that set up the aggregate's initial state)
 *          .when(the command being tested)
 *          .expectEvents(events that should be emitted)
 *          .expectState(assertions on the aggregate's in-memory state);
 * </pre>
 *
 * <p>For creation commands (no prior history): {@code fixture.givenNoPriorActivity()}
 *
 * <h2>Event Sourcing significance of these tests</h2>
 * The "given" events are replayed through {@code @EventSourcingHandler} methods before
 * the "when" command runs. This directly tests the event sourcing reconstruction logic:
 * if {@code on(AccountOpenedEvent)} doesn't set the correct balance, later commands will fail.
 */
@DisplayName("BankAccountAggregate unit tests")
class BankAccountAggregateTest {

    /** The Axon test fixture — bootstraps an in-memory command/event bus for the aggregate. */
    private FixtureConfiguration<BankAccountAggregate> fixture;

    /** Fixed test constants used across test methods. */
    private static final String ACCOUNT_ID = "test-account-001";
    private static final String OWNER_NAME = "Alice Smith";
    private static final BigDecimal INITIAL_DEPOSIT = new BigDecimal("500.00");
    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("250.00");
    private static final BigDecimal WITHDRAW_AMOUNT = new BigDecimal("100.00");

    @BeforeEach
    void setUp() {
        // Create a fresh fixture for each test — ensures full isolation
        fixture = new AggregateTestFixture<>(BankAccountAggregate.class);
    }

    // =========================================================================
    //  OpenAccountCommand tests
    // =========================================================================

    @Nested
    @DisplayName("OpenAccountCommand")
    class OpenAccountCommandTests {

        @Test
        @DisplayName("should emit AccountOpenedEvent with ACTIVE status and correct balance")
        void shouldEmitAccountOpenedEvent() {
            // given: no prior activity (this is a creation command)
            // when: we open a new account
            // then: AccountOpenedEvent should be emitted and aggregate state should be ACTIVE
            fixture.givenNoPriorActivity()
                    .when(new OpenAccountCommand(ACCOUNT_ID, OWNER_NAME, INITIAL_DEPOSIT))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate -> {
                        assertThat(aggregate.getAccountId()).isEqualTo(ACCOUNT_ID);
                        assertThat(aggregate.getOwnerName()).isEqualTo(OWNER_NAME);
                        assertThat(aggregate.getBalance()).isEqualByComparingTo(INITIAL_DEPOSIT);
                        assertThat(aggregate.getStatus()).isEqualTo(AccountStatus.ACTIVE);
                    });
        }

        @Test
        @DisplayName("should allow zero initial deposit (free account opening)")
        void shouldAllowZeroInitialDeposit() {
            fixture.givenNoPriorActivity()
                    .when(new OpenAccountCommand(ACCOUNT_ID, OWNER_NAME, BigDecimal.ZERO))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getBalance()).isEqualByComparingTo(BigDecimal.ZERO)
                    );
        }

        @Test
        @DisplayName("should reject OpenAccountCommand with negative initial deposit")
        void shouldRejectNegativeInitialDeposit() {
            fixture.givenNoPriorActivity()
                    .when(new OpenAccountCommand(ACCOUNT_ID, OWNER_NAME, new BigDecimal("-100.00")))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage(containsString("Initial deposit cannot be negative"));
        }

        @Test
        @DisplayName("should reject OpenAccountCommand with null initial deposit")
        void shouldRejectNullInitialDeposit() {
            fixture.givenNoPriorActivity()
                    .when(new OpenAccountCommand(ACCOUNT_ID, OWNER_NAME, null))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage(containsString("Initial deposit cannot be negative"));
        }

        @Test
        @DisplayName("should reject OpenAccountCommand with blank owner name")
        void shouldRejectBlankOwnerName() {
            fixture.givenNoPriorActivity()
                    .when(new OpenAccountCommand(ACCOUNT_ID, "  ", INITIAL_DEPOSIT))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage(containsString("Owner name must not be blank"));
        }

        @Test
        @DisplayName("should reject OpenAccountCommand with null owner name")
        void shouldRejectNullOwnerName() {
            fixture.givenNoPriorActivity()
                    .when(new OpenAccountCommand(ACCOUNT_ID, null, INITIAL_DEPOSIT))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage(containsString("Owner name must not be blank"));
        }
    }

    // =========================================================================
    //  DepositMoneyCommand tests
    // =========================================================================

    @Nested
    @DisplayName("DepositMoneyCommand")
    class DepositMoneyCommandTests {

        /** A pre-built AccountOpenedEvent to use as the "given" state. */
        private AccountOpenedEvent openedEvent() {
            return new AccountOpenedEvent(ACCOUNT_ID, OWNER_NAME, INITIAL_DEPOSIT, null);
        }

        @Test
        @DisplayName("should increase balance by the deposited amount")
        void shouldIncreaseBalance() {
            // given: an open account with 500.00 balance
            // when: deposit 250.00
            // then: aggregate balance should be 750.00
            fixture.given(openedEvent())
                    .when(new DepositMoneyCommand(ACCOUNT_ID, DEPOSIT_AMOUNT, "Salary"))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getBalance())
                                    .isEqualByComparingTo(INITIAL_DEPOSIT.add(DEPOSIT_AMOUNT))
                    );
        }

        @Test
        @DisplayName("should reject deposit with zero amount")
        void shouldRejectZeroDeposit() {
            fixture.given(openedEvent())
                    .when(new DepositMoneyCommand(ACCOUNT_ID, BigDecimal.ZERO, "Zero"))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage(containsString("Deposit amount must be positive"));
        }

        @Test
        @DisplayName("should reject deposit with negative amount")
        void shouldRejectNegativeDeposit() {
            fixture.given(openedEvent())
                    .when(new DepositMoneyCommand(ACCOUNT_ID, new BigDecimal("-50.00"), null))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage(containsString("Deposit amount must be positive"));
        }

        @Test
        @DisplayName("should reject deposit to a closed account")
        void shouldRejectDepositToClosedAccount() {
            // given: account was opened and then closed
            fixture.given(
                            openedEvent(),
                            new AccountClosedEvent(ACCOUNT_ID, INITIAL_DEPOSIT, "Closing", null)
                    )
                    .when(new DepositMoneyCommand(ACCOUNT_ID, DEPOSIT_AMOUNT, "After close"))
                    .expectException(IllegalStateException.class)
                    .expectExceptionMessage(containsString("Cannot deposit to closed account"));
        }

        @Test
        @DisplayName("should allow deposit without a description")
        void shouldAllowDepositWithoutDescription() {
            fixture.given(openedEvent())
                    .when(new DepositMoneyCommand(ACCOUNT_ID, DEPOSIT_AMOUNT, null))
                    .expectSuccessfulHandlerExecution();
        }
    }

    // =========================================================================
    //  WithdrawMoneyCommand tests
    // =========================================================================

    @Nested
    @DisplayName("WithdrawMoneyCommand")
    class WithdrawMoneyCommandTests {

        private AccountOpenedEvent openedEvent() {
            return new AccountOpenedEvent(ACCOUNT_ID, OWNER_NAME, INITIAL_DEPOSIT, null);
        }

        @Test
        @DisplayName("should decrease balance by the withdrawn amount")
        void shouldDecreaseBalance() {
            // given: account with 500.00
            // when: withdraw 100.00
            // then: balance should be 400.00
            fixture.given(openedEvent())
                    .when(new WithdrawMoneyCommand(ACCOUNT_ID, WITHDRAW_AMOUNT, "ATM"))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getBalance())
                                    .isEqualByComparingTo(INITIAL_DEPOSIT.subtract(WITHDRAW_AMOUNT))
                    );
        }

        @Test
        @DisplayName("should allow withdrawing the entire balance (zero result)")
        void shouldAllowWithdrawingEntireBalance() {
            fixture.given(openedEvent())
                    .when(new WithdrawMoneyCommand(ACCOUNT_ID, INITIAL_DEPOSIT, "Full withdrawal"))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getBalance()).isEqualByComparingTo(BigDecimal.ZERO)
                    );
        }

        @Test
        @DisplayName("should reject withdrawal that would overdraft the account")
        void shouldRejectOverdraft() {
            // given: account with 500.00
            // when: try to withdraw 600.00 (more than balance)
            // then: InsufficientFundsException
            fixture.given(openedEvent())
                    .when(new WithdrawMoneyCommand(ACCOUNT_ID, new BigDecimal("600.00"), "Overdraft attempt"))
                    .expectException(InsufficientFundsException.class)
                    .expectExceptionMessage(containsString("Insufficient funds"));
        }

        @Test
        @DisplayName("should reject withdrawal with zero amount")
        void shouldRejectZeroWithdrawal() {
            fixture.given(openedEvent())
                    .when(new WithdrawMoneyCommand(ACCOUNT_ID, BigDecimal.ZERO, null))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage(containsString("Withdrawal amount must be positive"));
        }

        @Test
        @DisplayName("should reject withdrawal with negative amount")
        void shouldRejectNegativeWithdrawal() {
            fixture.given(openedEvent())
                    .when(new WithdrawMoneyCommand(ACCOUNT_ID, new BigDecimal("-10.00"), null))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage(containsString("Withdrawal amount must be positive"));
        }

        @Test
        @DisplayName("should reject withdrawal from a closed account")
        void shouldRejectWithdrawalFromClosedAccount() {
            fixture.given(
                            openedEvent(),
                            new AccountClosedEvent(ACCOUNT_ID, INITIAL_DEPOSIT, "Closing", null)
                    )
                    .when(new WithdrawMoneyCommand(ACCOUNT_ID, WITHDRAW_AMOUNT, "After close"))
                    .expectException(IllegalStateException.class)
                    .expectExceptionMessage(containsString("Cannot withdraw from closed account"));
        }

        @Test
        @DisplayName("balance after multiple deposits and withdrawals should be correct")
        void shouldComputeBalanceCorrectlyAfterMultipleOperations() {
            // This test demonstrates the core Event Sourcing principle:
            // the balance is computed by replaying all past events.
            // given: opened with 500, deposited 200, withdrawn 150 → balance = 550
            BigDecimal expectedBalance = new BigDecimal("550.00");
            fixture.given(
                            openedEvent(),                                                                   // balance: 500
                            new MoneyDepositedEvent(ACCOUNT_ID, new BigDecimal("200.00"),
                                    new BigDecimal("700.00"), "Bonus", null),          // balance: 700
                            new MoneyWithdrawnEvent(ACCOUNT_ID, new BigDecimal("150.00"),
                                    new BigDecimal("550.00"), "Bills", null)           // balance: 550
                    )
                    .when(new WithdrawMoneyCommand(ACCOUNT_ID, new BigDecimal("50.00"), "Coffee"))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getBalance())
                                    .isEqualByComparingTo(expectedBalance.subtract(new BigDecimal("50.00")))
                    );
        }
    }

    // =========================================================================
    //  CloseAccountCommand tests
    // =========================================================================

    @Nested
    @DisplayName("CloseAccountCommand")
    class CloseAccountCommandTests {

        private AccountOpenedEvent openedEvent() {
            return new AccountOpenedEvent(ACCOUNT_ID, OWNER_NAME, INITIAL_DEPOSIT, null);
        }

        @Test
        @DisplayName("should emit AccountClosedEvent and transition to CLOSED status")
        void shouldCloseActiveAccount() {
            // given: an open account
            // when: close it
            // then: status should be CLOSED
            fixture.given(openedEvent())
                    .when(new CloseAccountCommand(ACCOUNT_ID, "Account no longer needed"))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getStatus()).isEqualTo(AccountStatus.CLOSED)
                    );
        }

        @Test
        @DisplayName("should allow closing an account without a reason")
        void shouldAllowClosingWithoutReason() {
            fixture.given(openedEvent())
                    .when(new CloseAccountCommand(ACCOUNT_ID, null))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getStatus()).isEqualTo(AccountStatus.CLOSED)
                    );
        }

        @Test
        @DisplayName("should reject CloseAccountCommand when account is already CLOSED")
        void shouldRejectClosingAlreadyClosedAccount() {
            // given: account is already closed
            fixture.given(
                            openedEvent(),
                            new AccountClosedEvent(ACCOUNT_ID, INITIAL_DEPOSIT, "First closure", null)
                    )
                    .when(new CloseAccountCommand(ACCOUNT_ID, "Second closure attempt"))
                    .expectException(IllegalStateException.class)
                    .expectExceptionMessage(containsString("Account is already closed"));
        }
    }

    // =========================================================================
    //  Event Sourcing state reconstruction tests
    // =========================================================================

    @Nested
    @DisplayName("Event Sourcing state reconstruction")
    class EventSourcingReconstructionTests {

        /**
         * This is the most important test class — it directly validates the core
         * Event Sourcing promise: replaying events reconstructs the correct state.
         *
         * <p>If the @EventSourcingHandler methods don't work correctly, these tests
         * will catch the inconsistencies.
         */

        @Test
        @DisplayName("should reconstruct ACTIVE state from AccountOpenedEvent alone")
        void shouldReconstructActiveStateFromOpenedEvent() {
            // If state reconstruction works, a deposit command should succeed
            fixture.given(new AccountOpenedEvent(ACCOUNT_ID, OWNER_NAME, INITIAL_DEPOSIT, null))
                    .when(new DepositMoneyCommand(ACCOUNT_ID, new BigDecimal("10.00"), null))
                    .expectSuccessfulHandlerExecution();
        }

        @Test
        @DisplayName("should reconstruct CLOSED state — further commands must be rejected")
        void shouldReconstructClosedState() {
            // If CLOSED state is correctly reconstructed, a deposit after closure must fail
            fixture.given(
                            new AccountOpenedEvent(ACCOUNT_ID, OWNER_NAME, INITIAL_DEPOSIT, null),
                            new AccountClosedEvent(ACCOUNT_ID, INITIAL_DEPOSIT, "closed", null)
                    )
                    .when(new DepositMoneyCommand(ACCOUNT_ID, new BigDecimal("10.00"), null))
                    .expectException(IllegalStateException.class);
        }

        @Test
        @DisplayName("balance reconstructed from event sequence must allow overdraft detection")
        void shouldReconstructBalanceForOverdraftDetection() {
            // given: account opened with 100, then 50 withdrawn → balance is 50
            // when: try to withdraw 75 (more than 50)
            // then: InsufficientFundsException — proves balance was correctly reconstructed
            fixture.given(
                            new AccountOpenedEvent(ACCOUNT_ID, OWNER_NAME, new BigDecimal("100.00"), null),
                            new MoneyWithdrawnEvent(ACCOUNT_ID, new BigDecimal("50.00"),
                                    new BigDecimal("50.00"), "First withdrawal", null)
                    )
                    .when(new WithdrawMoneyCommand(ACCOUNT_ID, new BigDecimal("75.00"), "Second"))
                    .expectException(InsufficientFundsException.class);
        }

        @Test
        @DisplayName("balance reconstructed from deposits must be cumulative")
        void shouldAccumulateDepositsCorrectly() {
            // given: opened with 0, deposited 100, deposited 200 → balance is 300
            // when: withdraw 300 (exactly the full balance)
            // then: success — proves cumulative balance reconstruction
            fixture.given(
                            new AccountOpenedEvent(ACCOUNT_ID, OWNER_NAME, BigDecimal.ZERO, null),
                            new MoneyDepositedEvent(ACCOUNT_ID, new BigDecimal("100.00"),
                                    new BigDecimal("100.00"), "First", null),
                            new MoneyDepositedEvent(ACCOUNT_ID, new BigDecimal("200.00"),
                                    new BigDecimal("300.00"), "Second", null)
                    )
                    .when(new WithdrawMoneyCommand(ACCOUNT_ID, new BigDecimal("300.00"), "Full"))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getBalance()).isEqualByComparingTo(BigDecimal.ZERO)
                    );
        }
    }
}
