package com.minipay.gateway;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
                "services.auth-url=http://localhost:${wiremock.server.port}",
                "services.wallet-url=http://localhost:${wiremock.server.port}",
                "jwt.secret=change-this-in-production-must-be-at-least-256-bits-long"
        }
)
@AutoConfigureWireMock(port = 0)
@AutoConfigureWebTestClient
@Testcontainers
@ActiveProfiles("test")
class ApiGatewayIntegrationTest {

    private static final String JWT_SECRET = "change-this-in-production-must-be-at-least-256-bits-long";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    WebTestClient webTestClient;

    @BeforeEach
    void resetWireMock() {
        WireMock.reset();
    }

    private String validJwt(String userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    @Nested
    class Actuator {

        @Test
        void healthEndpoint_returns200() {
            webTestClient.get().uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    class PublicEndpoints {

        @Test
        void login_gatewayForwardsToWireMockAndReturns200() {
            stubFor(post(urlEqualTo("/api/v1/auth/login"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            webTestClient.post().uri("/api/v1/auth/login")
                    .exchange()
                    .expectStatus().isOk();

            verify(1, postRequestedFor(urlEqualTo("/api/v1/auth/login")));
        }
    }

    @Nested
    class AuthenticatedEndpoints {

        @Test
        void logout_withoutToken_returns401AndWireMockNeverCalled() {
            webTestClient.post().uri("/api/v1/auth/logout")
                    .exchange()
                    .expectStatus().isUnauthorized();

            verify(0, postRequestedFor(urlEqualTo("/api/v1/auth/logout")));
        }

        @Test
        void logout_withInvalidToken_returns401() {
            webTestClient.post().uri("/api/v1/auth/logout")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void logout_withValidToken_forwardsToWireMock() {
            stubFor(post(urlEqualTo("/api/v1/auth/logout"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            webTestClient.post().uri("/api/v1/auth/logout")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt("user-123", "CUSTOMER"))
                    .exchange()
                    .expectStatus().isOk();

            verify(1, postRequestedFor(urlEqualTo("/api/v1/auth/logout")));
        }

        @Test
        void wallets_withoutToken_returns401() {
            webTestClient.get().uri("/api/v1/wallets/my-wallet")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}
