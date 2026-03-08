package com.fabiankaraben.spring.basic.jacksoncustomserializer.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Deserializes a formatted currency String back into an Integer value (cents).
 * Example: "$10.00" -> 1000
 */
public class MoneyDeserializer extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        if (value == null || value.isEmpty()) {
            return null;
        }
        // Remove non-numeric characters except dot and minus (for negative values)
        // This regex removes '$', ',', etc.
        String cleanValue = value.replaceAll("[^\\d.-]", "");
        try {
            BigDecimal amount = new BigDecimal(cleanValue);
            return amount.multiply(BigDecimal.valueOf(100)).intValue();
        } catch (NumberFormatException e) {
            throw new IOException("Invalid money format: " + value, e);
        }
    }
}
