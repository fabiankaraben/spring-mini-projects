package com.example.customresponsebodyadvice;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Custom ResponseBodyAdvice implementation that intercepts API responses
 * and adds additional metadata before the response is serialized.
 *
 * This advice specifically targets responses that are Maps, adding a "processedAt"
 * timestamp to indicate when the response was processed by the server.
 *
 * ResponseBodyAdvice allows us to modify the response body after the controller
 * has returned it but before it is written to the HTTP response.
 */
@ControllerAdvice
public class CustomResponseBodyAdvice implements ResponseBodyAdvice<Map<String, Object>> {

    /**
     * Determines whether this advice should be applied to the given method and converter.
     * This implementation supports all methods that return Map<String, Object>.
     *
     * @param returnType the return type of the controller method
     * @param converterType the selected converter type
     * @return true if this advice should be applied, false otherwise
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Apply this advice to methods that return Map<String, Object>
        return Map.class.isAssignableFrom(returnType.getParameterType());
    }

    /**
     * Modifies the response body before it is written to the HTTP response.
     * Adds a "processedAt" field with the current timestamp.
     *
     * @param body the body returned by the controller method
     * @param returnType the return type of the controller method
     * @param selectedContentType the content type selected through content negotiation
     * @param selectedConverterType the converter type selected to write the response
     * @param request the current request
     * @param response the current response
     * @return the modified response body
     */
    @Override
    public Map<String, Object> beforeBodyWrite(
            Map<String, Object> body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        if (body != null) {
            // Add processing timestamp to the response
            body.put("processedAt", Instant.now().toString());
        }

        return body;
    }
}
