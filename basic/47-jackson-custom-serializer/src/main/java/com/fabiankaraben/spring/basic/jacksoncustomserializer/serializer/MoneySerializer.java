package com.fabiankaraben.spring.basic.jacksoncustomserializer.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Serializes an Integer value (representing cents) into a formatted currency String.
 * Example: 1000 -> "$10.00"
 */
public class MoneySerializer extends JsonSerializer<Integer> {
    @Override
    public void serialize(Integer value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            BigDecimal amount = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100));
            String formatted = NumberFormat.getCurrencyInstance(Locale.US).format(amount);
            gen.writeString(formatted);
        }
    }
}
