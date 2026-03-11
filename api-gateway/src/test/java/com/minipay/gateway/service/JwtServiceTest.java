package com.minipay.gateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha";

    private JwtService jwtService;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String mintToken(String subject, String role, long expiryMs) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(signingKey)
                .compact();
    }

    @Nested
    class ValidateToken {

        @Test
        void validToken_returnsClaimsWithCorrectSubjectAndRole() {
            String token = mintToken("user-123", "CUSTOMER", 60_000);

            Claims claims = jwtService.validateToken(token);

            assertThat(claims.getSubject()).isEqualTo("user-123");
            assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        }

        @Test
        void expiredToken_throwsJwtException() {
            String token = mintToken("user-123", "CUSTOMER", -1000);

            assertThatThrownBy(() -> jwtService.validateToken(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        void tokenSignedWithWrongKey_throwsJwtException() {
            String wrongSecret = "wrong-secret-key-that-is-at-least-256-bits-long-for-hmac-sha";
            SecretKey wrongKey = Keys.hmacShaKeyFor(wrongSecret.getBytes(StandardCharsets.UTF_8));
            String token = Jwts.builder()
                    .subject("user-123")
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(wrongKey)
                    .compact();

            assertThatThrownBy(() -> jwtService.validateToken(token))
                    .isInstanceOf(JwtException.class);
        }
    }
}
