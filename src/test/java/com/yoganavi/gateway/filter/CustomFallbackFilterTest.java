package com.yoganavi.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFallbackFilterTest {

    private CustomFallbackFilter customFallbackFilter;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private CircuitBreaker.Metrics metrics;

    @Mock
    private GatewayFilterChain chain;

    private ServerWebExchange exchange; // 테스트용 HTTP 요청/응답 교환

    @BeforeEach
    void setUp() {
        customFallbackFilter = new CustomFallbackFilter(objectMapper, circuitBreakerRegistry);

        MockServerHttpRequest request = MockServerHttpRequest // 테스트용 HTTP GET 요청
            .get("/user/test/error")
            .build();
        exchange = MockServerWebExchange.from(request);

        // Circuit Breaker 기본 설정
        given(circuitBreakerRegistry.circuitBreaker(anyString())).willReturn(circuitBreaker);
        given(circuitBreaker.getMetrics()).willReturn(metrics);
        given(circuitBreaker.getName()).willReturn("user-service-test");
    }

    @Test
    void 정상_케이스_에러_없이_응답_전달() {
        // 필터 체인이 정상적으로 완료되도록
        given(chain.filter(exchange)).willReturn(Mono.empty());
        GatewayFilter filter = customFallbackFilter.apply(new CustomFallbackFilter.Config());

        // 필터 실행
        Mono<Void> result = filter.filter(exchange, chain);

        // 필터 체인이 정상 완료되고 에러 처리가 발생하지 않음
        StepVerifier.create(result)
            .verifyComplete();
        verify(chain).filter(exchange);
    }

    @Test
    void 서킷브레이커_OPEN_상태_CallNotPermittedException_처리() throws Exception {
        // Circuit Breaker 설정 및 OPEN 상태 시뮬레이션
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .build();

        // Mock CircuitBreaker에 config 설정
        when(circuitBreaker.getCircuitBreakerConfig()).thenReturn(config);

        // CallNotPermittedException 생성 -- OPEN 상태에서 발생하는 예외 시뮬레이션
        CallNotPermittedException exception = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);

        given(chain.filter(exchange)).willReturn(Mono.error(exception));
        given(circuitBreaker.getState()).willReturn(CircuitBreaker.State.OPEN);
        given(objectMapper.writeValueAsBytes(any())).willReturn("{}".getBytes());

        GatewayFilter filter = customFallbackFilter.apply(new CustomFallbackFilter.Config());

        // when
        Mono<Void> result = filter.filter(exchange, chain);

        // 에러 응답이 정상적으로 처리되었는지
        StepVerifier.create(result)
            .verifyComplete();

        verify(chain).filter(exchange);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void 서비스_호출_실패_일반_예외_처리() throws Exception {
        // 서비스 호출 실패 시나리오
        RuntimeException exception = new RuntimeException("Service Error");
        given(chain.filter(exchange)).willReturn(Mono.error(exception));
        given(circuitBreaker.getState()).willReturn(CircuitBreaker.State.CLOSED);
        given(objectMapper.writeValueAsBytes(any())).willReturn("{}".getBytes());

        GatewayFilter filter = customFallbackFilter.apply(new CustomFallbackFilter.Config());

        // 필터 실행
        Mono<Void> result = filter.filter(exchange, chain);

        // 에러가 503 Service Unavailable로 처리되는지
        StepVerifier.create(result)
            .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void 실패_임계치_도달로_인한_상태_전환_확인() throws Exception {
        //  Circuit Breaker 상태 전환 시나리오 설정
        RuntimeException exception = new RuntimeException("Service Error");
        given(chain.filter(exchange)).willReturn(Mono.error(exception));
        // 상태가 CLOSED에서 OPEN으로 전환
        given(circuitBreaker.getState())
            .willReturn(CircuitBreaker.State.CLOSED)
            .willReturn(CircuitBreaker.State.OPEN);
        given(objectMapper.writeValueAsBytes(any())).willReturn("{}".getBytes());

        GatewayFilter filter = customFallbackFilter.apply(new CustomFallbackFilter.Config());

        // 필터 실행 및 상태 전환 검증
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Circuit Breaker 상태가 두 번 확인되었는지
        verify(circuitBreaker, times(2)).getState();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}