package com.fabiankaraben.spring.basic.jacksoncustomserializer.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MoneySerializerTest {

    @Mock
    private JsonGenerator jsonGenerator;

    @Mock
    private SerializerProvider serializerProvider;

    private final MoneySerializer serializer = new MoneySerializer();

    @Test
    void shouldSerializePositiveAmount() throws IOException {
        // 1000 cents -> $10.00
        serializer.serialize(1000, jsonGenerator, serializerProvider);
        verify(jsonGenerator).writeString("$10.00");
    }

    @Test
    void shouldSerializeZero() throws IOException {
        // 0 cents -> $0.00
        serializer.serialize(0, jsonGenerator, serializerProvider);
        verify(jsonGenerator).writeString("$0.00");
    }

    @Test
    void shouldSerializeNegativeAmount() throws IOException {
        // -550 cents -> -$5.50
        serializer.serialize(-550, jsonGenerator, serializerProvider);
        verify(jsonGenerator).writeString("-$5.50");
    }

    @Test
    void shouldSerializeNull() throws IOException {
        serializer.serialize(null, jsonGenerator, serializerProvider);
        verify(jsonGenerator).writeNull();
    }
}
