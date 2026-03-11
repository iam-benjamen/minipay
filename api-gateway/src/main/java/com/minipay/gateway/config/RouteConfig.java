package com.minipay.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class RouteConfig {

    private final String authUrl;
    private final String walletUrl;

    public RouteConfig(
            @Value("${services.auth-url}") String authUrl,
            @Value("${services.wallet-url}") String walletUrl) {
        this.authUrl = authUrl;
        this.walletUrl = walletUrl;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-register", r -> r.path("/api/v1/auth/register").and().method(HttpMethod.POST).uri(authUrl))
                .route("auth-login",    r -> r.path("/api/v1/auth/login").and().method(HttpMethod.POST).uri(authUrl))
                .route("auth-refresh",  r -> r.path("/api/v1/auth/refresh").and().method(HttpMethod.POST).uri(authUrl))
                .route("auth-logout",   r -> r.path("/api/v1/auth/logout").and().method(HttpMethod.POST).uri(authUrl))
                .route("auth-catchall", r -> r.path("/api/v1/auth/**").uri(authUrl))
                .route("wallets",       r -> r.path("/api/v1/wallets/**").uri(walletUrl))
                .build();
    }
}
