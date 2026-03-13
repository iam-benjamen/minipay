package com.minipay.wallet.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiInterceptor implements HandlerInterceptor {

    private final byte[] secret;

    public InternalApiInterceptor(@Value("${internal.api.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("X-Internal-Secret");
        byte[] provided = header != null ? header.getBytes(StandardCharsets.UTF_8) : new byte[0];

        if (!MessageDigest.isEqual(secret, provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\",\"data\":null}");
            return false;
        }
        return true;
    }
}
