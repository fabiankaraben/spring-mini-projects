package com.example.vaultsecrets.service;

import com.example.vaultsecrets.domain.CredentialEntry;
import com.example.vaultsecrets.dto.StoreSecretRequest;
import com.example.vaultsecrets.repository.CredentialEntryRepository;
import com.example.vaultsecrets.vault.VaultOperationsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CredentialService}.
 *
 * <p>Uses Mockito to replace both {@link VaultOperationsService} and
 * {@link CredentialEntryRepository} with mocks, so these tests run
 * instantly without any Vault server, Docker, Spring context, or database.</p>
 *
 * <p>{@code @ExtendWith(MockitoExtension.class)} activates Mockito's JUnit 5
 * extension, which initialises {@code @Mock} and {@code @InjectMocks} fields
 * before each test method.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CredentialService unit tests")
class CredentialServiceTest {

    /** Mock Vault operations — no real Vault connection needed. */
    @Mock
    private VaultOperationsService vaultOperationsService;

    /** Mock JPA repository — no real database needed. */
    @Mock
    private CredentialEntryRepository credentialEntryRepository;

    /** The real service under test, with mock dependencies injected. */
    @InjectMocks
    private CredentialService credentialService;

    /** Shared test fixtures. */
    private CredentialEntry dbEntry;
    private CredentialEntry apiEntry;

    @BeforeEach
    void setUp() {
        dbEntry = new CredentialEntry(
                "prod-db", "secret/myapp/db", "Production DB credentials");
        apiEntry = new CredentialEntry(
                "payment-api", "secret/myapp/payment", "Payment API key");
    }

    // -------------------------------------------------------------------------
    // storeSecret tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("storeSecret should write to Vault and save metadata to the repository")
    void storeSecret_writesVaultAndSavesMetadata() {
        // Arrange: no existing credential with this name
        when(credentialEntryRepository.existsByName("prod-db")).thenReturn(false);
        // Repository save returns the saved entity
        when(credentialEntryRepository.save(any(CredentialEntry.class))).thenReturn(dbEntry);

        StoreSecretRequest request = new StoreSecretRequest(
                "prod-db", "secret/myapp/db", "Production DB credentials",
                Map.of("username", "admin", "password", "s3cr3t")
        );

        // Act
        CredentialEntry result = credentialService.storeSecret(request);

        // Assert: Vault write was called once with the correct path
        verify(vaultOperationsService, times(1))
                .writeSecret(eq("secret/myapp/db"), anyMap());

        // Assert: repository.save was called once
        verify(credentialEntryRepository, times(1)).save(any(CredentialEntry.class));

        // Assert: returned entity has the expected fields
        assertThat(result.getName()).isEqualTo("prod-db");
        assertThat(result.getVaultPath()).isEqualTo("secret/myapp/db");
    }

    @Test
    @DisplayName("storeSecret should throw IllegalArgumentException when name already exists")
    void storeSecret_throwsWhenNameDuplicate() {
        // Arrange: credential with this name already exists
        when(credentialEntryRepository.existsByName("prod-db")).thenReturn(true);

        StoreSecretRequest request = new StoreSecretRequest(
                "prod-db", "secret/myapp/db", null,
                Map.of("username", "admin")
        );

        // Act + Assert
        assertThatThrownBy(() -> credentialService.storeSecret(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prod-db")
                .hasMessageContaining("already exists");

        // Vault should NOT be called when validation fails
        verifyNoInteractions(vaultOperationsService);
        // Repository save should NOT be called
        verify(credentialEntryRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // listCredentials tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("listCredentials should return all entries from the repository")
    void listCredentials_returnsAllEntries() {
        when(credentialEntryRepository.findAll()).thenReturn(List.of(dbEntry, apiEntry));

        List<CredentialEntry> result = credentialService.listCredentials();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CredentialEntry::getName)
                .containsExactlyInAnyOrder("prod-db", "payment-api");
    }

    @Test
    @DisplayName("listCredentials should return empty list when no credentials are registered")
    void listCredentials_returnsEmptyList() {
        when(credentialEntryRepository.findAll()).thenReturn(List.of());

        List<CredentialEntry> result = credentialService.listCredentials();

        assertThat(result).isEmpty();
        // Vault should never be called when listing metadata
        verifyNoInteractions(vaultOperationsService);
    }

    // -------------------------------------------------------------------------
    // getSecretByName tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getSecretByName should return secret data from Vault when credential exists")
    void getSecretByName_returnsSecretFromVault() {
        when(credentialEntryRepository.findByName("prod-db")).thenReturn(Optional.of(dbEntry));
        Map<String, Object> secretData = Map.of("username", "admin", "password", "s3cr3t");
        when(vaultOperationsService.readSecret("secret/myapp/db")).thenReturn(secretData);

        Map<String, Object> result = credentialService.getSecretByName("prod-db");

        assertThat(result).containsEntry("username", "admin");
        assertThat(result).containsEntry("password", "s3cr3t");
        verify(vaultOperationsService, times(1)).readSecret("secret/myapp/db");
    }

    @Test
    @DisplayName("getSecretByName should throw IllegalArgumentException when name is not registered")
    void getSecretByName_throwsWhenNameNotFound() {
        when(credentialEntryRepository.findByName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> credentialService.getSecretByName("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");

        // Vault should not be called if metadata lookup fails
        verifyNoInteractions(vaultOperationsService);
    }

    @Test
    @DisplayName("getSecretByName should throw IllegalStateException when Vault returns null")
    void getSecretByName_throwsWhenVaultReturnsNull() {
        when(credentialEntryRepository.findByName("prod-db")).thenReturn(Optional.of(dbEntry));
        // Vault returns null — secret was deleted directly in Vault, but metadata still exists
        when(vaultOperationsService.readSecret(anyString())).thenReturn(null);

        assertThatThrownBy(() -> credentialService.getSecretByName("prod-db"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vault")
                .hasMessageContaining("secret/myapp/db");
    }

    // -------------------------------------------------------------------------
    // getMetadataByName tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMetadataByName should return Optional present when entry exists")
    void getMetadataByName_returnsPresentOptional() {
        when(credentialEntryRepository.findByName("prod-db")).thenReturn(Optional.of(dbEntry));

        Optional<CredentialEntry> result = credentialService.getMetadataByName("prod-db");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("prod-db");
        // Vault should never be called for metadata-only lookup
        verifyNoInteractions(vaultOperationsService);
    }

    @Test
    @DisplayName("getMetadataByName should return empty Optional when entry does not exist")
    void getMetadataByName_returnsEmptyOptional() {
        when(credentialEntryRepository.findByName("unknown")).thenReturn(Optional.empty());

        Optional<CredentialEntry> result = credentialService.getMetadataByName("unknown");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // deleteCredential tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deleteCredential should call Vault delete and repository delete")
    void deleteCredential_deletesFromVaultAndRepository() {
        when(credentialEntryRepository.findByName("prod-db")).thenReturn(Optional.of(dbEntry));
        doNothing().when(vaultOperationsService).deleteSecret(anyString());
        doNothing().when(credentialEntryRepository).delete(dbEntry);

        credentialService.deleteCredential("prod-db");

        // Both Vault and repository delete should be called
        verify(vaultOperationsService, times(1)).deleteSecret("secret/myapp/db");
        verify(credentialEntryRepository, times(1)).delete(dbEntry);
    }

    @Test
    @DisplayName("deleteCredential should throw IllegalArgumentException when credential not found")
    void deleteCredential_throwsWhenNotFound() {
        when(credentialEntryRepository.findByName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> credentialService.deleteCredential("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");

        // Vault delete should NOT be called
        verifyNoInteractions(vaultOperationsService);
        // Repository delete should NOT be called
        verify(credentialEntryRepository, never()).delete(any());
    }
}
