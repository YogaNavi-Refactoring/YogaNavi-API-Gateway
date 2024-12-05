package com.yoganavi.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class RequestLoggingFilter extends AbstractGatewayFilterFactory<Object> {

    public RequestLoggingFilter() {
        super(Object.class);
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String requestPath = request.getPath().value();
            String requestMethod = request.getMethod().name();
            String requestId = request.getId();

            // 요청 시작 로그
            log.info("[{}] >>> 요청 시작: {} {}", requestId, requestMethod, requestPath);
            request.getHeaders().forEach((key, value) ->
                log.debug("[{}] 요청 헤더 - {}: {}", requestId, key, value));

            return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    ServerHttpResponse response = exchange.getResponse();
                    // 응답 완료 로그
                    log.info("[{}] <<< 응답 완료: {} {} - Status: {}",
                        requestId,
                        requestMethod,
                        requestPath,
                        response.getStatusCode());

                    if (log.isDebugEnabled()) {
                        response.getHeaders().forEach((key, value) ->
                            log.debug("[{}] 응답 헤더 - {}: {}", requestId, key, value));
                    }
                }));
        };
    }
}