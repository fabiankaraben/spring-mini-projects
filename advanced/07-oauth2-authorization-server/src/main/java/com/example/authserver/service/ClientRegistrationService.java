package com.example.authserver.service;

import com.example.authserver.dto.ClientInfoResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for querying the OAuth2 registered client registry.
 *
 * <p>Spring Authorization Server stores registered client data in the
 * {@code oauth2_registered_client} table (created automatically by
 * {@code spring.sql.init.mode=always} from the schema scripts bundled with
 * the Spring Authorization Server library).
 *
 * <p>This service reads client metadata directly via {@link JdbcTemplate} rather than
 * through {@code RegisteredClientRepository}. This is intentional:
 * <ul>
 *   <li>We only need the {@code client_id} and {@code authorization_grant_types} columns.</li>
 *   <li>Using raw JDBC for reads avoids the overhead of deserializing the full
 *       {@code RegisteredClient} object (which includes token settings, redirect URIs, etc.).</li>
 * </ul>
 *
 * <p>The grant types are stored as a comma-separated string in the database.
 * This service parses that string and returns a clean list for the API response.
 */
@Service
public class ClientRegistrationService {

    /**
     * Spring JDBC template for executing SQL queries.
     * Backed by the configured DataSource (PostgreSQL in production, H2 in tests).
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Constructs the service with its required JDBC template.
     *
     * @param jdbcTemplate Spring JDBC template for database access
     */
    public ClientRegistrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrieves a list of all registered OAuth2 clients with their grant types.
     *
     * <p>Queries the {@code oauth2_registered_client} table which is created by
     * Spring Authorization Server's embedded SQL schema. The table columns used:
     * <ul>
     *   <li>{@code client_id}               — the public identifier of the client</li>
     *   <li>{@code authorization_grant_types}— a comma-separated list of grant type values</li>
     * </ul>
     *
     * <p>Example grant type values: {@code authorization_code,refresh_token,client_credentials}
     *
     * <p>The result is ordered by {@code client_id} for consistent output.
     *
     * @return a list of {@link ClientInfoResponse} records (never null, may be empty)
     */
    public List<ClientInfoResponse> listRegisteredClients() {
        String sql = "SELECT client_id, authorization_grant_types " +
                     "FROM oauth2_registered_client " +
                     "ORDER BY client_id";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String clientId = rs.getString("client_id");

            // The authorization_grant_types column stores a comma-separated list.
            // Example value: "authorization_code,refresh_token,client_credentials"
            // We split it, trim whitespace, and collect into a List<String>.
            String grantTypesRaw = rs.getString("authorization_grant_types");
            List<String> grantTypes = List.of();
            if (grantTypesRaw != null && !grantTypesRaw.isBlank()) {
                grantTypes = List.of(grantTypesRaw.split(","))
                        .stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .sorted() // sorted for deterministic output
                        .toList();
            }

            return new ClientInfoResponse(clientId, grantTypes);
        });
    }
}
