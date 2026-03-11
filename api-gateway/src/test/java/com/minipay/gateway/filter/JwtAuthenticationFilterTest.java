package com.minipay.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.gateway.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
        filter = new JwtAuthenticationFilter(jwtService, new ObjectMapper());
    }

    @Nested
    class PublicPaths {

        @Test
        void registerPath_chainCalledWithoutInvokingJwtService() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/register").build());

            filter.filter(exchange, chain).block();

            verify(chain).filter(any());
            verifyNoInteractions(jwtService);
        }

        @Test
        void loginPath_chainCalledWithoutInvokingJwtService() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/login").build());

            filter.filter(exchange, chain).block();

            verify(chain).filter(any());
            verifyNoInteractions(jwtService);
        }

        @Test
        void refreshPath_chainCalledWithoutInvokingJwtService() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/refresh").build());

            filter.filter(exchange, chain).block();

            verify(chain).filter(any());
            verifyNoInteractions(jwtService);
        }
    }

    @Nested
    class ProtectedPaths {

        @Test
        void noAuthorizationHeader_returns401WithoutCallingChain() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/logout").build());

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(chain);
        }

        @Test
        void authorizationHeaderWithoutBearerPrefix_returns401() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(chain);
        }

        @Test
        void jwtServiceThrowsJwtException_returns401() {
            when(jwtService.validateToken(anyString())).thenThrow(new JwtException("invalid"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(chain);
        }

        @Test
        void validToken_chainCalledWithInjectedHeaders() {
            Claims claims = mock(Claims.class);
            when(claims.getSubject()).thenReturn("user-123");
            when(claims.get("role", String.class)).thenReturn("CUSTOMER");
            when(jwtService.validateToken("valid-token")).thenReturn(claims);

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                            .build());

            filter.filter(exchange, chain).block();

            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            ServerWebExchange captured = captor.getValue();
            assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-123");
            assertThat(captured.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("CUSTOMER");
        }
    }
}
