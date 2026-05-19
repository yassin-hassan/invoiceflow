package com.example.invoiceflow.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestContext {

    private static final int USER_AGENT_MAX = 512;

    @Value("${audit.trust-proxy:false}")
    private boolean trustProxy;

    public String ipAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;

        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }
        return request.getRemoteAddr();
    }

    public String userAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > USER_AGENT_MAX ? ua.substring(0, USER_AGENT_MAX) : ua;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
