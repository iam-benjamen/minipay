package com.minipay.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
@Order(-2)
public class RequestLoggingFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String requestId = request.getHeaders().getFirst("X-Request-ID");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        final String finalRequestId = requestId;
        final long startTime = System.currentTimeMillis();

        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Request-ID", finalRequestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        return chain.filter(mutatedExchange).doFinally(signal -> {
            long duration = System.currentTimeMillis() - startTime;
            var status = mutatedExchange.getResponse().getStatusCode();
            log.info("requestId={} method={} path={} status={} duration={}ms",
                    finalRequestId,
                    request.getMethod().name(),
                    request.getPath().value(),
                    status != null ? status.value() : "unknown",
                    duration);
        });
    }
}
