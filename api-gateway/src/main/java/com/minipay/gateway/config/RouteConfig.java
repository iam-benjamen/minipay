package com.minipay.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-register", r -> r
                        .path("/api/v1/auth/register").and().method(HttpMethod.POST)
                        .uri("http://localhost:8081"))
                .route("auth-login", r -> r
                        .path("/api/v1/auth/login").and().method(HttpMethod.POST)
                        .uri("http://localhost:8081"))
                .route("auth-refresh", r -> r
                        .path("/api/v1/auth/refresh").and().method(HttpMethod.POST)
                        .uri("http://localhost:8081"))
                .route("auth-logout", r -> r
                        .path("/api/v1/auth/logout").and().method(HttpMethod.POST)
                        .uri("http://localhost:8081"))
                .route("auth-catchall", r -> r
                        .path("/api/v1/auth/**")
                        .uri("http://localhost:8081"))
                .route("wallets", r -> r
                        .path("/api/v1/wallets/**")
                        .uri("http://localhost:8082"))
                .build();
    }
}
