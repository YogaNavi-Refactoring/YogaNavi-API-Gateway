package com.yoganavi.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class ResilienceConfig {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .slidingWindowSize(10) // 상태를 결정하는데 사용할 최근 호출 수
                .minimumNumberOfCalls(5) // Circuit Breaker가 작동하기 위한 최소 호출 수
                .permittedNumberOfCallsInHalfOpenState(3) // Half-Open 상태에서 허용할 호출 수
                .waitDurationInOpenState(Duration.ofSeconds(5)) // Circuit이 Open 상태를 유지하는 시간
                .failureRateThreshold(50.0f) // Circuit을 Open 상태로 전환하는 실패율 임계값(%)
                .build())
            .build());
    }
}