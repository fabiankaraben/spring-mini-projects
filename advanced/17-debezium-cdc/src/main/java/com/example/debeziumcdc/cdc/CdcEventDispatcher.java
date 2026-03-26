package com.example.debeziumcdc.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Bridges Debezium change events to Kafka.
 *
 * <p>This component implements {@link DebeziumEngine.ChangeConsumer}, which means
 * Debezium calls {@link #handleBatch(List, DebeziumEngine.RecordCommitter)} with
 * each batch of {@link ChangeEvent} records read from the PostgreSQL WAL.
 *
 * <p>Processing pipeline per record:
 * <ol>
 *   <li>Parse the JSON key and value produced by Debezium's JsonConverter.</li>
 *   <li>Extract the operation code ({@code op}) from the envelope.</li>
 *   <li>Build a {@link ProductCdcEvent} from the {@code before}/{@code after} structs.</li>
 *   <li>Publish the event to the Kafka topic {@code product-cdc-events}.</li>
 *   <li>Commit the record offset so Debezium does not re-deliver it on restart.</li>
 * </ol>
 *
 * <p>Debezium JSON envelope structure (after JsonConverter):
 * <pre>
 * {
 *   "op": "c",          // c=create, u=update, d=delete, r=read (snapshot)
 *   "before": { ... },  // null for INSERT
 *   "after":  { ... },  // null for DELETE
 *   "source": { ... },  // metadata: database, table, transaction id, lsn, etc.
 *   "ts_ms": 1234567890 // event timestamp in milliseconds
 * }
 * </pre>
 */
@Component
public class CdcEventDispatcher implements DebeziumEngine.ChangeConsumer<ChangeEvent<String, String>> {

    private static final Logger log = LoggerFactory.getLogger(CdcEventDispatcher.class);

    /** Kafka topic where CDC events are published. */
    public static final String CDC_TOPIC = "product-cdc-events";

    private final KafkaTemplate<String, ProductCdcEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CdcEventDispatcher(KafkaTemplate<String, ProductCdcEvent> kafkaTemplate,
                              ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Called by the Debezium engine for each batch of change events read from the WAL.
     *
     * <p>We process each record individually and commit it immediately after publishing
     * to Kafka. This provides at-least-once delivery semantics: if the process crashes
     * after publishing but before committing, Debezium will re-deliver the same record
     * on restart.
     *
     * @param records   list of change events from the WAL
     * @param committer used to mark each record as processed
     */
    @Override
    public void handleBatch(List<ChangeEvent<String, String>> records,
                            DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer)
            throws InterruptedException {

        for (ChangeEvent<String, String> record : records) {
            try {
                handleRecord(record);
            } catch (Exception e) {
                // Log and continue — do not let a single bad record block the pipeline.
                // In production you would send to a dead-letter topic here.
                log.error("Failed to process CDC record: key={}, error={}", record.key(), e.getMessage(), e);
            } finally {
                // Commit each record individually so Debezium advances the replication
                // slot offset even if the Kafka publish fails.
                committer.markProcessed(record);
            }
        }

        // Flush the batch offset to the replication slot after all records are processed.
        committer.markBatchFinished();
    }

    /**
     * Processes a single Debezium change event.
     *
     * <p>The value is a JSON string representing the Debezium envelope. If the
     * value is null, Debezium sent a tombstone record (used to indicate the key
     * was deleted in a compacted topic) — we skip those.
     *
     * @param record the Debezium change event
     */
    @SuppressWarnings("unchecked")
    private void handleRecord(ChangeEvent<String, String> record) throws Exception {
        String valueJson = record.value();

        if (valueJson == null) {
            // Tombstone record — the key has been deleted from a compacted topic.
            // This happens after Debezium emits a DELETE and the topic is compacted.
            log.debug("Skipping tombstone record for key={}", record.key());
            return;
        }

        // Parse the full Debezium envelope as a generic Map.
        // We use Map<String, Object> because the envelope contains nested maps
        // (before/after structs) and we need to access them dynamically.
        Map<String, Object> envelope = objectMapper.readValue(valueJson, Map.class);

        // Extract the "payload" wrapper that JsonConverter produces when
        // schemas are included. If schemas are disabled, the envelope IS the payload.
        Map<String, Object> payload = extractPayload(envelope);

        if (payload == null || !payload.containsKey("op")) {
            log.debug("Skipping record without 'op' field: key={}", record.key());
            return;
        }

        // Parse the operation code and build the typed event
        String opCode = (String) payload.get("op");
        CdcOperation operation = CdcOperation.fromDebeziumCode(opCode);

        Map<String, Object> beforeMap = (Map<String, Object>) payload.get("before");
        Map<String, Object> afterMap  = (Map<String, Object>) payload.get("after");

        ProductCdcEvent.ProductSnapshot before = toSnapshot(beforeMap);
        ProductCdcEvent.ProductSnapshot after  = toSnapshot(afterMap);

        ProductCdcEvent event = new ProductCdcEvent(operation, before, after, Instant.now());

        log.info("CDC event captured: op={}, productId={}",
                operation,
                after != null ? after.getId() : (before != null ? before.getId() : "?"));

        // Use the product ID as the Kafka message key for partition ordering.
        // All events for the same product always go to the same partition, ensuring
        // that consumers process events for a product in order.
        String messageKey = extractMessageKey(before, after);

        ProducerRecord<String, ProductCdcEvent> kafkaRecord =
                new ProducerRecord<>(CDC_TOPIC, messageKey, event);

        kafkaTemplate.send(kafkaRecord);
    }

    /**
     * Extracts the payload map from the Debezium envelope.
     *
     * <p>When Debezium's JsonConverter is configured with {@code schemas.enable=true}
     * (the default), the top-level object has a {@code schema} and a {@code payload}
     * wrapper. When {@code schemas.enable=false}, the top-level object IS the payload.
     *
     * <p>We configure the connector with {@code schemas.enable=false} (see
     * {@link com.example.debeziumcdc.config.DebeziumConnectorConfig}) so this method
     * returns the envelope itself.
     *
     * @param envelope the parsed top-level JSON object
     * @return the payload map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> envelope) {
        if (envelope.containsKey("payload")) {
            // schemas.enable=true: unwrap the payload
            return (Map<String, Object>) envelope.get("payload");
        }
        // schemas.enable=false: the envelope IS the payload
        return envelope;
    }

    /**
     * Converts a raw Debezium field map (from the "before" or "after" struct) into
     * a typed {@link ProductCdcEvent.ProductSnapshot}.
     *
     * <p>PostgreSQL numeric columns are sent as strings by the JsonConverter when
     * no schema registry is used. We parse them explicitly.
     *
     * @param fieldMap the raw field map, or null if no state is present
     * @return a {@link ProductCdcEvent.ProductSnapshot}, or null if fieldMap is null
     */
    private ProductCdcEvent.ProductSnapshot toSnapshot(Map<String, Object> fieldMap) {
        if (fieldMap == null) {
            return null;
        }

        // id: PostgreSQL BIGINT → Long
        Long id = fieldMap.get("id") == null
                ? null
                : ((Number) fieldMap.get("id")).longValue();

        String name        = (String) fieldMap.get("name");
        String description = (String) fieldMap.get("description");

        // price: PostgreSQL NUMERIC → may arrive as String or Number depending on
        // whether the logical decoding plugin sends it as text or numeric.
        BigDecimal price = null;
        Object rawPrice = fieldMap.get("price");
        if (rawPrice != null) {
            price = rawPrice instanceof Number
                    ? BigDecimal.valueOf(((Number) rawPrice).doubleValue())
                    : new BigDecimal(rawPrice.toString());
        }

        // stock: PostgreSQL INTEGER → Integer
        int stock = fieldMap.get("stock") == null
                ? 0
                : ((Number) fieldMap.get("stock")).intValue();

        return new ProductCdcEvent.ProductSnapshot(id, name, description, price, stock);
    }

    /**
     * Derives the Kafka message key from the product snapshot.
     *
     * <p>Using the product ID as the key guarantees partition ordering: all events
     * for the same product (e.g., CREATE then UPDATE then DELETE) land in the same
     * partition and are consumed in order.
     *
     * @param before snapshot before the change (may be null)
     * @param after  snapshot after the change (may be null)
     * @return the product ID as a string, or {@code "unknown"} if neither is available
     */
    private String extractMessageKey(ProductCdcEvent.ProductSnapshot before,
                                     ProductCdcEvent.ProductSnapshot after) {
        if (after != null && after.getId() != null) {
            return String.valueOf(after.getId());
        }
        if (before != null && before.getId() != null) {
            return String.valueOf(before.getId());
        }
        return "unknown";
    }
}
