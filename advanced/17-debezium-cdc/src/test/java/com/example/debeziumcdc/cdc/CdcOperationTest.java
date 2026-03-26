package com.example.debeziumcdc.cdc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CdcOperation}.
 *
 * <p>Verifies that the Debezium operation code mapping works correctly for all
 * known codes and throws an appropriate exception for unknown codes.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Each valid Debezium op code maps to the correct {@link CdcOperation}.</li>
 *   <li>An unknown op code throws {@link IllegalArgumentException}.</li>
 * </ul>
 */
@DisplayName("CdcOperation enum unit tests")
class CdcOperationTest {

    // =========================================================================
    // Valid operation code mapping
    // =========================================================================

    @ParameterizedTest(name = "Debezium code ''{0}'' maps to {1}")
    @CsvSource({
            "c, CREATE",
            "u, UPDATE",
            "d, DELETE",
            "r, READ"
    })
    @DisplayName("fromDebeziumCode maps all valid Debezium codes correctly")
    void fromDebeziumCodeMapsValidCodes(String code, CdcOperation expected) {
        CdcOperation result = CdcOperation.fromDebeziumCode(code);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("fromDebeziumCode('c') returns CREATE")
    void fromDebeziumCodeCreate() {
        assertThat(CdcOperation.fromDebeziumCode("c")).isEqualTo(CdcOperation.CREATE);
    }

    @Test
    @DisplayName("fromDebeziumCode('u') returns UPDATE")
    void fromDebeziumCodeUpdate() {
        assertThat(CdcOperation.fromDebeziumCode("u")).isEqualTo(CdcOperation.UPDATE);
    }

    @Test
    @DisplayName("fromDebeziumCode('d') returns DELETE")
    void fromDebeziumCodeDelete() {
        assertThat(CdcOperation.fromDebeziumCode("d")).isEqualTo(CdcOperation.DELETE);
    }

    @Test
    @DisplayName("fromDebeziumCode('r') returns READ")
    void fromDebeziumCodeRead() {
        assertThat(CdcOperation.fromDebeziumCode("r")).isEqualTo(CdcOperation.READ);
    }

    // =========================================================================
    // Invalid operation code
    // =========================================================================

    @Test
    @DisplayName("fromDebeziumCode throws IllegalArgumentException for unknown code")
    void fromDebeziumCodeThrowsForUnknownCode() {
        assertThatThrownBy(() -> CdcOperation.fromDebeziumCode("x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Debezium operation code: x");
    }

    @Test
    @DisplayName("fromDebeziumCode throws IllegalArgumentException for empty string")
    void fromDebeziumCodeThrowsForEmptyString() {
        assertThatThrownBy(() -> CdcOperation.fromDebeziumCode(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fromDebeziumCode throws IllegalArgumentException for uppercase code")
    void fromDebeziumCodeThrowsForUppercaseCode() {
        // Debezium always uses lowercase; uppercase should not match
        assertThatThrownBy(() -> CdcOperation.fromDebeziumCode("C"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
