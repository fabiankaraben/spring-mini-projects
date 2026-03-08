package com.example.demo.advice;

import com.example.demo.wrapper.ResponseWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalResponseBodyAdvice}.
 * <p>
 * This class tests the logic of the advice component in isolation, using Mockito to mock dependencies.
 * It verifies that the advice correctly wraps objects and handles String responses by serializing them to JSON.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class GlobalResponseBodyAdviceTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GlobalResponseBodyAdvice advice;

    @Mock
    private MethodParameter returnType;
    
    @SuppressWarnings("unchecked")
    private Class<? extends HttpMessageConverter<?>> converterType = (Class<? extends HttpMessageConverter<?>>) (Class<?>) HttpMessageConverter.class;
    
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;

    @Test
    void supports_shouldReturnTrue() {
        assertTrue(advice.supports(returnType, converterType));
    }

    @Test
    void beforeBodyWrite_shouldReturnWrappedBody_whenBodyIsObject() {
        Object body = new Object();
        Object result = advice.beforeBodyWrite(body, returnType, MediaType.APPLICATION_JSON, converterType, request, response);

        assertTrue(result instanceof ResponseWrapper);
        ResponseWrapper<?> wrapper = (ResponseWrapper<?>) result;
        assertEquals(body, wrapper.getData());
        assertEquals(200, wrapper.getStatus());
        assertEquals("Success", wrapper.getMessage());
        assertNotNull(wrapper.getTimestamp());
    }

    @Test
    void beforeBodyWrite_shouldReturnOriginalBody_whenBodyIsAlreadyWrapped() {
        ResponseWrapper<String> body = ResponseWrapper.success("test");
        Object result = advice.beforeBodyWrite(body, returnType, MediaType.APPLICATION_JSON, converterType, request, response);

        assertEquals(body, result);
    }

    @Test
    void beforeBodyWrite_shouldReturnJsonString_whenBodyIsString() throws JsonProcessingException {
        String body = "test string";
        String jsonResponse = "{\"status\":200,\"message\":\"Success\",\"data\":\"test string\"}";
        
        when(objectMapper.writeValueAsString(any(ResponseWrapper.class))).thenReturn(jsonResponse);

        Object result = advice.beforeBodyWrite(body, returnType, MediaType.APPLICATION_JSON, converterType, request, response);

        assertEquals(jsonResponse, result);
    }
}
