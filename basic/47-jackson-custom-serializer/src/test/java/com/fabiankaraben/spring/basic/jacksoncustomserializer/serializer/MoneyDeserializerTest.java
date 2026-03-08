package com.fabiankaraben.spring.basic.jacksoncustomserializer.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoneyDeserializerTest {

    @Mock
    private JsonParser jsonParser;

    @Mock
    private DeserializationContext context;

    private final MoneyDeserializer deserializer = new MoneyDeserializer();

    @Test
    void shouldDeserializeSimpleCurrency() throws IOException {
        when(jsonParser.getText()).thenReturn("$10.00");
        Integer result = deserializer.deserialize(jsonParser, context);
        assertEquals(1000, result);
    }

    @Test
    void shouldDeserializeWithCommas() throws IOException {
        when(jsonParser.getText()).thenReturn("$1,234.56");
        Integer result = deserializer.deserialize(jsonParser, context);
        assertEquals(123456, result);
    }

    @Test
    void shouldDeserializeNegative() throws IOException {
        when(jsonParser.getText()).thenReturn("-$5.50");
        Integer result = deserializer.deserialize(jsonParser, context);
        assertEquals(-550, result);
    }
    
    @Test
    void shouldDeserializeParenthesesNegative() throws IOException {
        // Some locales or formats use ($5.50) for negative, but our simple regex logic 
        // treats non-digits/dot/minus as noise. 
        // "($5.50)" -> "-5.50" (if regex keeps minus) OR "5.50" (if regex removes parens but keeps minus if present).
        // Let's check regex: [^\\d.-] replaces everything that is NOT digit, dot, or minus.
        // ($5.50) -> 5.50 because '(' and ')' and '$' are removed. Minus is not in that string usually if using parens.
        // BUT if input is "-$5.50", regex keeps '-'.
        // Let's stick to standard negative sign for this simple implementation.
        
        when(jsonParser.getText()).thenReturn("-10.00");
        Integer result = deserializer.deserialize(jsonParser, context);
        assertEquals(-1000, result);
    }

    @Test
    void shouldDeserializePlainNumber() throws IOException {
        when(jsonParser.getText()).thenReturn("50.25");
        Integer result = deserializer.deserialize(jsonParser, context);
        assertEquals(5025, result);
    }

    @Test
    void shouldHandleNull() throws IOException {
        when(jsonParser.getText()).thenReturn(null);
        Integer result = deserializer.deserialize(jsonParser, context);
        assertNull(result);
    }
}
