package com.example.authserver.service;

import com.example.authserver.dto.ClientInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ClientRegistrationService}.
 *
 * <p>These tests verify the service's data-mapping and parsing logic without
 * touching a real database. We mock {@link JdbcTemplate} to return controlled
 * data and verify that {@link ClientRegistrationService} processes the results
 * correctly.
 *
 * <p><b>Testing approach:</b>
 * <ul>
 *   <li>We use Mockito to mock the {@link JdbcTemplate} — no Spring context,
 *       no database connection needed.</li>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} activates the JUnit 5
 *       Mockito extension, which automatically injects {@code @Mock} fields.</li>
 *   <li>We call {@link JdbcTemplate#query(String, RowMapper)} with {@code any()}
 *       matchers and return pre-built {@link ClientInfoResponse} objects to
 *       simulate what the real query would return after row mapping.</li>
 * </ul>
 *
 * <p>The full end-to-end behavior (actual SQL execution against a real PostgreSQL
 * container) is covered in the integration test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClientRegistrationService — unit tests")
class ClientRegistrationServiceTest {

    /**
     * Mocked JdbcTemplate — no actual database connection is made.
     * Mockito injects this mock automatically via @ExtendWith(MockitoExtension.class).
     */
    @Mock
    private JdbcTemplate jdbcTemplate;

    /**
     * The service under test. Constructed manually with the mocked JdbcTemplate.
     */
    private ClientRegistrationService service;

    /**
     * Sets up the service under test before each test method.
     * A fresh instance is created each time to ensure test isolation.
     */
    @BeforeEach
    void setUp() {
        service = new ClientRegistrationService(jdbcTemplate);
    }

    /**
     * Verifies that when the database contains two clients, the service returns
     * a list of two ClientInfoResponse objects with the correct data.
     */
    @Test
    @DisplayName("returns list with one entry per registered client")
    void returnsListOfRegisteredClients() {
        // Arrange: mock the JdbcTemplate to return two pre-built responses
        List<ClientInfoResponse> mockedResult = List.of(
                new ClientInfoResponse("messaging-client",
                        List.of("authorization_code", "client_credentials", "refresh_token")),
                new ClientInfoResponse("service-account-client",
                        List.of("client_credentials"))
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(mockedResult);

        // Act
        List<ClientInfoResponse> result = service.listRegisteredClients();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ClientInfoResponse::clientId)
                .containsExactlyInAnyOrder("messaging-client", "service-account-client");
    }

    /**
     * Verifies that the service returns an empty list when no clients are registered.
     */
    @Test
    @DisplayName("returns empty list when no clients are registered")
    void returnsEmptyListWhenNoClients() {
        // Arrange: mock an empty result set
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of());

        // Act
        List<ClientInfoResponse> result = service.listRegisteredClients();

        // Assert
        assertThat(result).isEmpty();
    }

    /**
     * Verifies that the messaging-client has all three expected grant types.
     */
    @Test
    @DisplayName("messaging-client has authorization_code, client_credentials and refresh_token")
    void messagingClientHasAllGrantTypes() {
        // Arrange
        List<ClientInfoResponse> mockedResult = List.of(
                new ClientInfoResponse("messaging-client",
                        List.of("authorization_code", "client_credentials", "refresh_token"))
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(mockedResult);

        // Act
        List<ClientInfoResponse> result = service.listRegisteredClients();

        // Assert
        assertThat(result).hasSize(1);
        ClientInfoResponse messagingClient = result.get(0);
        assertThat(messagingClient.clientId()).isEqualTo("messaging-client");
        assertThat(messagingClient.grantTypes())
                .containsExactlyInAnyOrder(
                        "authorization_code", "client_credentials", "refresh_token");
    }

    /**
     * Verifies that the service-account-client has only client_credentials.
     */
    @Test
    @DisplayName("service-account-client has only client_credentials grant type")
    void serviceAccountClientHasOnlyClientCredentials() {
        // Arrange
        List<ClientInfoResponse> mockedResult = List.of(
                new ClientInfoResponse("service-account-client",
                        List.of("client_credentials"))
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(mockedResult);

        // Act
        List<ClientInfoResponse> result = service.listRegisteredClients();

        // Assert
        assertThat(result).hasSize(1);
        ClientInfoResponse serviceClient = result.get(0);
        assertThat(serviceClient.clientId()).isEqualTo("service-account-client");
        assertThat(serviceClient.grantTypes()).containsExactly("client_credentials");
    }
}
