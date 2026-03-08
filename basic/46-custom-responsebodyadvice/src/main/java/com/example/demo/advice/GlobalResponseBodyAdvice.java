package com.example.demo.advice;

import com.example.demo.wrapper.ResponseWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;


/**
 * Global advice to intercept and modify the response body before it is written to the client.
 * <p>
 * This class implements {@link ResponseBodyAdvice} to intercept all responses from {@link org.springframework.web.bind.annotation.RestController}s.
 * It wraps the original response body into a {@link ResponseWrapper} object to ensure a consistent response format
 * (containing timestamp, status, message, and data) across the application.
 * </p>
 */
@ControllerAdvice
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Determines whether this component supports the given controller method return type and converter type.
     *
     * @param returnType    The return type of the controller method.
     * @param converterType The selected converter type.
     * @return {@code true} if this advice should be applied; {@code false} otherwise.
     *         Here we return {@code true} to apply it to all responses.
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Apply to all responses
        return true;
    }

    /**
     * Invoked after the controller method execution but before the body is written to the response.
     *
     * @param body                  The body returned by the controller method.
     * @param returnType            The return type of the controller method.
     * @param selectedContentType   The content type selected through content negotiation.
     * @param selectedConverterType The converter type selected to write to the response.
     * @param request               The current request.
     * @param response              The current response.
     * @return The modified body (a {@link ResponseWrapper}) to be written to the response.
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {

        // Avoid wrapping if it's already a ResponseWrapper (e.g. from an exception handler)
        if (body instanceof ResponseWrapper) {
            return body;
        }

        // Special handling for String return types because Spring uses StringHttpMessageConverter
        // If the return type is String, Spring will try to use StringHttpMessageConverter which
        // cannot serialize our ResponseWrapper object to a String automatically.
        // Therefore, we manually serialize the wrapper to a JSON string.
        if (body instanceof String) {
            try {
                return objectMapper.writeValueAsString(ResponseWrapper.success(body));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error writing JSON", e);
            }
        }

        return ResponseWrapper.success(body);
    }
}
