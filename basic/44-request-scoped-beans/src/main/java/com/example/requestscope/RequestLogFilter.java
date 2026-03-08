package com.example.requestscope;

import jakarta.servlet.*;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RequestLogFilter implements Filter {

    private final RequestData requestData;

    public RequestLogFilter(RequestData requestData) {
        this.requestData = requestData;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        // We can access the request-scoped bean in the filter
        requestData.addLog("Filter: Request intercepted");
        
        chain.doFilter(request, response);
        
        requestData.addLog("Filter: Request finished");
    }
}
