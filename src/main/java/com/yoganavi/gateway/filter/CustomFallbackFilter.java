package com.yoganavi.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서비스 호출 실패 시 폴백 처리 담당
 *
 * 요청 처리 시나리오
 * 1. 서킷브레이커 CLOSED + 서비스 실패 시
 *    Request
 *    → CircuitBreakerFilter (CLOSED 상태라서 통과)
 *    → 서비스 호출 시도
 *    → 500 에러 발생
 *    → CustomFallbackFilter가 에러 잡아서 503 응답
 *    → 서킷브레이커가 실패 카운트 증가
 *
 * 2. 서킷브레이커 OPEN 상태일 때
 *    Request
 *    → CircuitBreakerFilter (OPEN 상태 확인)
 *    → CallNotPermittedException 발생
 *    → 서비스 호출 시도도 안함
 *    → CustomFallbackFilter가 예외 잡아서 503 응답
 */
@Component
@Slf4j
public class CustomFallbackFilter extends AbstractGatewayFilterFactory<CustomFallbackFilter.Config> {

    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CustomFallbackFilter(ObjectMapper objectMapper, CircuitBreakerRegistry circuitBreakerRegistry) {
        super(Config.class);
        this.objectMapper = objectMapper;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 요청 경로와 Circuit Breaker 이름 확인
            String path = exchange.getRequest().getPath().value();
            String cbName = extractCircuitBreakerName(path);

            // Circuit Breaker 현재 상태 로깅
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(cbName);
            logCircuitBreakerState(circuitBreaker, path);

            // 요청 처리 시도
            return chain.filter(exchange)
                .onErrorResume(error -> { // 에러가 발생했을 때만 실행, 정상 처리되면 이 부분은 스킵되고 원래 응답 그대로 반환
                    // 에러 발생 시 처리
                    // 1. CircuitBreaker가 요청을 차단한 경우 (OPEN 상태)
                    if (error instanceof CallNotPermittedException) {
                        log.warn("서킷 브레이커 [{}] 가 오픈됨. {}로의 요청이 블록됨.",
                            cbName, path);
                    }
                    // 2. 실제 서비스 호출 실패한 경우 (CLOSED 상태에서 실패)
                    else {
                        log.error("경로 {}로의 서비스 호출이 실패함: {}",
                            path, error.getMessage());

                        // Circuit Breaker 상태가 변경되었는지 확인
                        CircuitBreaker.State currentState = circuitBreaker.getState();
                        if (currentState != CircuitBreaker.State.CLOSED) {
                            log.info("Error로 인해 서킷 브레이커 [{}] 상태가 {} 로 변경됨",
                                cbName, currentState);
                        }
                    }

                    // 폴백 응답 생성
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("timestamp", LocalDateTime.now());
                    response.put("path", path);
                    response.put("message", "서비스 일시적으로 사용 불가");
                    response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
                    response.put("error", error.getClass().getSimpleName());

                    // 응답 헤더
                    exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

                    // 응답 본문
                    byte[] bytes;
                    try {
                        bytes = objectMapper.writeValueAsBytes(response);
                    } catch (JsonProcessingException e) {
                        log.error("응답 생성 실패 에러", e);
                        bytes = "{}".getBytes();
                    }

                    // 최종 응답 반환
                    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                    return exchange.getResponse().writeWith(Mono.just(buffer));
                });
        };
    }

    /**
     * 요청 경로에서 Circuit Breaker 이름 추출
     * 각 경로별로 다른 Circuit Breaker 설정을 사용할 수 있도록 함
     */
    private String extractCircuitBreakerName(String path) {
        if (path.startsWith("/user/")) {
            return "user-service-test";
        }
        return "default";
    }

    /**
     * 로깅
     */
    private void logCircuitBreakerState(CircuitBreaker circuitBreaker, String path) {
        CircuitBreaker.State state = circuitBreaker.getState();
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        log.debug("서킷 브레이커 [{}] 상태 - 경로: {}, " +
                "현재 상태={}, " +
                "실패율={}, 느린 호출 비율={}, " +
                "느린 호출 횟수={}, 실패한 호출 횟수={}, " +
                "허용되지 않은 호출 횟수={}",
            circuitBreaker.getName(),
            path,
            state,
            metrics.getFailureRate(),
            metrics.getSlowCallRate(),
            metrics.getNumberOfSlowCalls(),
            metrics.getNumberOfFailedCalls(),
            metrics.getNumberOfNotPermittedCalls()
        );
    }

    public static class Config {
        // 필터 설정용
        // 나중에 필요 시 추가
    }
}