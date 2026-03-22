package com.example.eventsourcing.integration;

import com.example.eventsourcing.command.api.DepositMoneyCommand;
import com.example.eventsourcing.command.api.OpenAccountCommand;
import com.example.eventsourcing.command.api.WithdrawMoneyCommand;
import com.example.eventsourcing.query.api.FindAccountByIdQuery;
import com.example.eventsourcing.query.model.AccountSummary;
import com.example.eventsourcing.query.model.AccountSummaryRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Event Sourcing bank account system.
 *
 * <h2>What is tested here</h2>
 * <ul>
 *   <li>The complete command → event → projection pipeline with a real PostgreSQL database</li>
 *   <li>REST API endpoints (open, deposit, withdraw, close, query)</li>
 *   <li>Axon JPA event store persistence (domain_event_entry table)</li>
 *   <li>Aggregate reconstruction from stored events (the core Event Sourcing promise)</li>
 *   <li>Read model (AccountSummary) updates via the event projection</li>
 * </ul>
 *
 * <h2>Test infrastructure</h2>
 * <ul>
 *   <li>{@code @Testcontainers} + {@code @Container}: spins up a real PostgreSQL 16 container
 *       so Axon's JPA event store and the read model table are tested against a real DB.</li>
 *   <li>{@code @DynamicPropertySource}: injects the Testcontainers JDBC URL into the
 *       Spring context before beans are created.</li>
 *   <li>{@code @SpringBootTest}: loads the full application context.</li>
 *   <li>{@code @AutoConfigureMockMvc}: configures MockMvc for HTTP-level REST testing.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Bank Account Event Sourcing Integration Tests")
class BankAccountIntegrationTest {

    // =========================================================================
    //  Testcontainers — PostgreSQL
    // =========================================================================

    /**
     * Shared PostgreSQL container — started once per test class (not per test method).
     * Alpine variant is fine for PostgreSQL (multi-arch support on both AMD64 and ARM64).
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("axondb_test")
            .withUsername("axon")
            .withPassword("axon");

    /**
     * Injects the Testcontainers-assigned JDBC URL into the Spring context
     * before the application starts. This overrides the static URL in application-test.yml.
     */
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // =========================================================================
    //  Spring beans
    // =========================================================================

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private QueryGateway queryGateway;

    @Autowired
    private AccountSummaryRepository accountSummaryRepository;

    /**
     * JdbcTemplate for direct SQL operations — used to truncate the Axon event store
     * tables before each test (the JPA repository cannot reach the Axon-managed tables).
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // =========================================================================
    //  Setup
    // =========================================================================

    /**
     * Cleans both the Axon event store and the read-model table before each test.
     *
     * <p>Why we must truncate the event store:
     * Axon rejects creating a new aggregate with an ID that already exists in the
     * {@code domain_event_entry} table ("Cannot reuse aggregate identifier").
     * Even though each test generates a fresh UUID, the Testcontainers PostgreSQL
     * container is shared across the whole test class, so rows from previous test
     * runs would linger without this cleanup.
     *
     * <p>We use TRUNCATE ... CASCADE to remove all Axon-managed tables atomically.
     */
    @BeforeEach
    void setUp() {
        // Truncate Axon event store tables using DO block so the statement is
        // a no-op when the table doesn't exist yet (before Hibernate DDL first run).
        jdbcTemplate.execute(
                "DO $$ BEGIN " +
                "  IF EXISTS (SELECT FROM pg_tables WHERE tablename = 'domain_event_entry') " +
                "  THEN TRUNCATE TABLE domain_event_entry CASCADE; END IF; " +
                "END $$");
        jdbcTemplate.execute(
                "DO $$ BEGIN " +
                "  IF EXISTS (SELECT FROM pg_tables WHERE tablename = 'snapshot_event_entry') " +
                "  THEN TRUNCATE TABLE snapshot_event_entry CASCADE; END IF; " +
                "END $$");
        // Truncate the read model (query side)
        accountSummaryRepository.deleteAll();
    }

    // =========================================================================
    //  REST API tests — command side
    // =========================================================================

    @Test
    @DisplayName("POST /api/accounts should create an account and return 201")
    void shouldCreateAccountViaRestApi() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Bob Johnson",
                                  "initialDeposit": 1000.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Account opened successfully"));
    }

    @Test
    @DisplayName("POST /api/accounts should reject request with blank owner name")
    void shouldRejectBlankOwnerName() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "",
                                  "initialDeposit": 100.00
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/accounts should reject request with negative initial deposit")
    void shouldRejectNegativeInitialDeposit() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Test User",
                                  "initialDeposit": -100.00
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/accounts/{id} should return 404 for unknown account")
    void shouldReturn404ForUnknownAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/unknown-account-id"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    //  Full command → event → projection pipeline tests
    // =========================================================================

    @Test
    @DisplayName("Opening an account should create a read-model entry with ACTIVE status")
    void shouldCreateReadModelEntryOnAccountOpen() {
        // given: a unique account ID
        String accountId = UUID.randomUUID().toString();

        // when: send OpenAccountCommand via CommandGateway
        commandGateway.sendAndWait(new OpenAccountCommand(
                accountId, "Carol Williams", new BigDecimal("750.00")
        ));

        // then: read model should reflect the new account
        Optional<AccountSummary> summary = accountSummaryRepository.findById(accountId);
        assertThat(summary).isPresent();
        assertThat(summary.get().getOwnerName()).isEqualTo("Carol Williams");
        assertThat(summary.get().getBalance()).isEqualByComparingTo("750.00");
        assertThat(summary.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(summary.get().getOpenedAt()).isNotNull();
    }

    @Test
    @DisplayName("Depositing money should increase the balance in the read model")
    void shouldIncreaseBalanceOnDeposit() {
        // given: account with 500.00
        String accountId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new OpenAccountCommand(
                accountId, "David Brown", new BigDecimal("500.00")
        ));

        // when: deposit 250.00
        commandGateway.sendAndWait(new DepositMoneyCommand(accountId, new BigDecimal("250.00"), "Salary"));

        // then: balance in read model should be 750.00
        Optional<AccountSummary> summary = accountSummaryRepository.findById(accountId);
        assertThat(summary).isPresent();
        assertThat(summary.get().getBalance()).isEqualByComparingTo("750.00");
        assertThat(summary.get().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Withdrawing money should decrease the balance in the read model")
    void shouldDecreaseBalanceOnWithdrawal() {
        // given: account with 300.00
        String accountId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new OpenAccountCommand(
                accountId, "Eve Davis", new BigDecimal("300.00")
        ));

        // when: withdraw 80.00
        commandGateway.sendAndWait(new WithdrawMoneyCommand(accountId, new BigDecimal("80.00"), "Groceries"));

        // then: balance should be 220.00
        Optional<AccountSummary> summary = accountSummaryRepository.findById(accountId);
        assertThat(summary).isPresent();
        assertThat(summary.get().getBalance()).isEqualByComparingTo("220.00");
    }

    @Test
    @DisplayName("Closing an account should update status to CLOSED in read model")
    void shouldUpdateStatusToClosedOnAccountClose() throws Exception {
        // given: open an account via REST
        String responseBody = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerName": "Frank Miller", "initialDeposit": 100.00}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String accountId = extractAccountId(responseBody);

        // when: close the account via REST
        mockMvc.perform(delete("/api/accounts/" + accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Moving abroad"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account closed successfully"));

        // then: status should be CLOSED in the read model
        Optional<AccountSummary> summary = accountSummaryRepository.findById(accountId);
        assertThat(summary).isPresent();
        assertThat(summary.get().getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("GET /api/accounts/{id} should return account after opening")
    void shouldReturnAccountViaQueryEndpoint() throws Exception {
        // given: open an account via CommandGateway
        String accountId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new OpenAccountCommand(
                accountId, "Grace Lee", new BigDecimal("200.00")
        ));

        // when: query via REST
        mockMvc.perform(get("/api/accounts/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.ownerName").value("Grace Lee"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/accounts should return all accounts")
    void shouldReturnAllAccountsViaRestApi() throws Exception {
        // given: create two accounts
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new OpenAccountCommand(id1, "User One", new BigDecimal("100.00")));
        commandGateway.sendAndWait(new OpenAccountCommand(id2, "User Two", new BigDecimal("200.00")));

        // when: get all accounts
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("GET /api/accounts?status=ACTIVE should filter by status")
    void shouldFilterAccountsByStatus() throws Exception {
        // given: one active and one closed account
        String activeId = UUID.randomUUID().toString();
        String closedId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new OpenAccountCommand(activeId, "Active User", new BigDecimal("100.00")));
        commandGateway.sendAndWait(new OpenAccountCommand(closedId, "Closed User", new BigDecimal("50.00")));

        // Close the second account via REST
        mockMvc.perform(delete("/api/accounts/" + closedId))
                .andExpect(status().isOk());

        // when: filter by ACTIVE
        mockMvc.perform(get("/api/accounts?status=ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accountId", org.hamcrest.Matchers.hasItem(activeId)))
                .andExpect(jsonPath("$[*].accountId",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(closedId))));
    }

    @Test
    @DisplayName("Withdrawal exceeding balance should return 409 Conflict")
    void shouldReturn409OnOverdraft() throws Exception {
        // given: account with 100.00
        String accountId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new OpenAccountCommand(
                accountId, "Henry Wilson", new BigDecimal("100.00")
        ));

        // when: try to withdraw 500.00 (exceeds balance)
        mockMvc.perform(post("/api/accounts/" + accountId + "/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 500.00, "description": "Overdraft attempt"}
                                """))
                // then: 409 Conflict
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Deposit to a closed account should return 409 Conflict")
    void shouldReturn409OnDepositToClosedAccount() throws Exception {
        // given: open and close an account
        String accountId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new OpenAccountCommand(
                accountId, "Iris Taylor", new BigDecimal("50.00")
        ));
        mockMvc.perform(delete("/api/accounts/" + accountId)).andExpect(status().isOk());

        // when: try to deposit to the closed account
        mockMvc.perform(post("/api/accounts/" + accountId + "/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 100.00, "description": "After close"}
                                """))
                // then: 409 Conflict
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Closing an already-closed account should return 409 Conflict")
    void shouldReturn409OnDoubleClose() throws Exception {
        // given: open and close an account
        String accountId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new OpenAccountCommand(
                accountId, "Jack Anderson", new BigDecimal("25.00")
        ));
        mockMvc.perform(delete("/api/accounts/" + accountId)).andExpect(status().isOk());

        // when: try to close again
        mockMvc.perform(delete("/api/accounts/" + accountId))
                // then: 409 Conflict
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("QueryGateway: FindAccountByIdQuery should return account summary")
    void shouldReturnAccountViaQueryGateway() {
        // given: open an account
        String accountId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new OpenAccountCommand(
                accountId, "Karen Martinez", new BigDecimal("999.99")
        ));

        // when: query via QueryGateway directly
        Optional<AccountSummary> result = queryGateway.query(
                new FindAccountByIdQuery(accountId),
                ResponseTypes.optionalInstanceOf(AccountSummary.class)
        ).join();

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getOwnerName()).isEqualTo("Karen Martinez");
        assertThat(result.get().getBalance()).isEqualByComparingTo("999.99");
        assertThat(result.get().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Event Sourcing: aggregate state survives multiple deposits and withdrawals")
    void shouldMaintainCorrectBalanceAfterMultipleOperations() {
        // This test demonstrates the full Event Sourcing cycle:
        // Each operation emits an event; the aggregate state is reconstructed from those events.
        //
        // Timeline:
        //   Open with 1000.00
        //   Deposit 500.00  → balance: 1500.00
        //   Withdraw 200.00 → balance: 1300.00
        //   Deposit 100.00  → balance: 1400.00
        //   Withdraw 400.00 → balance: 1000.00
        String accountId = UUID.randomUUID().toString();

        commandGateway.sendAndWait(new OpenAccountCommand(accountId, "Leo Garcia", new BigDecimal("1000.00")));
        commandGateway.sendAndWait(new DepositMoneyCommand(accountId, new BigDecimal("500.00"), "Bonus"));
        commandGateway.sendAndWait(new WithdrawMoneyCommand(accountId, new BigDecimal("200.00"), "Rent"));
        commandGateway.sendAndWait(new DepositMoneyCommand(accountId, new BigDecimal("100.00"), "Refund"));
        commandGateway.sendAndWait(new WithdrawMoneyCommand(accountId, new BigDecimal("400.00"), "Holiday"));

        // then: read model should show 1000.00
        Optional<AccountSummary> summary = accountSummaryRepository.findById(accountId);
        assertThat(summary).isPresent();
        assertThat(summary.get().getBalance()).isEqualByComparingTo("1000.00");
        assertThat(summary.get().getStatus()).isEqualTo("ACTIVE");
    }

    // =========================================================================
    //  Helper methods
    // =========================================================================

    /**
     * Extracts the {@code accountId} field from a JSON response body.
     *
     * @param json e.g. {@code {"accountId":"abc-123","message":"..."}}
     * @return the extracted accountId value
     */
    private String extractAccountId(String json) {
        int start = json.indexOf("\"accountId\":\"") + "\"accountId\":\"".length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
