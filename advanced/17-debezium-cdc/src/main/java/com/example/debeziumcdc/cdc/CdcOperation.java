package com.example.debeziumcdc.cdc;

/**
 * Represents the type of database change (DML operation) captured by Debezium.
 *
 * <p>Debezium encodes the operation type in the {@code op} field of each
 * change event envelope:
 * <ul>
 *   <li>{@code "c"} — CREATE (INSERT)</li>
 *   <li>{@code "u"} — UPDATE</li>
 *   <li>{@code "d"} — DELETE</li>
 *   <li>{@code "r"} — READ (snapshot — initial data load)</li>
 * </ul>
 *
 * <p>This enum maps those single-character codes to more descriptive names
 * for use within the application's domain model.
 */
public enum CdcOperation {

    /**
     * A new row was inserted into the table.
     * Debezium op code: {@code "c"} (create).
     */
    CREATE,

    /**
     * An existing row was updated.
     * Debezium op code: {@code "u"} (update).
     * The before/after payload contains the old and new field values.
     */
    UPDATE,

    /**
     * An existing row was deleted.
     * Debezium op code: {@code "d"} (delete).
     * The after payload will be null; only the before payload is available.
     */
    DELETE,

    /**
     * A row was read during the initial snapshot.
     * Debezium op code: {@code "r"} (read).
     * Treated like CREATE for downstream consumers.
     */
    READ;

    /**
     * Converts a Debezium single-character operation code to a {@link CdcOperation}.
     *
     * @param opCode the Debezium operation code ("c", "u", "d", "r")
     * @return the corresponding {@link CdcOperation}
     * @throws IllegalArgumentException if the code is not recognised
     */
    public static CdcOperation fromDebeziumCode(String opCode) {
        return switch (opCode) {
            case "c" -> CREATE;
            case "u" -> UPDATE;
            case "d" -> DELETE;
            case "r" -> READ;
            default  -> throw new IllegalArgumentException(
                    "Unknown Debezium operation code: " + opCode);
        };
    }
}
