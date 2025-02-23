package com.yoganavi.gateway.filter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CircuitBreakerIntegrationTest {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        circuitBreaker = circuitBreakerRegistry.circuitBreaker("user-service-test");
        circuitBreaker.reset();
    }

    @Test
    void 정상_요청_처리_테스트() {
        webTestClient.get()
            .uri("/user/test/normal")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo("서킷 브레이커 테스트 - 일반 응답");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isPositive();
    }

    @Test
    void 연속_실패_후_서킷브레이커_동작_테스트() {
        // 연속 실패 요청 발생
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                .uri("/user/test/error")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.message").isEqualTo("서비스 일시적으로 사용 불가");
        }

        // 서킷브레이커 OPEN 상태로 전환
        await().atMost(2, TimeUnit.SECONDS)
            .until(() -> circuitBreaker.getState() == CircuitBreaker.State.OPEN);

        // OPEN 상태에서 정상 요청도 차단
        webTestClient.get()
            .uri("/user/test/normal")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody()
            .jsonPath("$.message").isEqualTo("서비스 일시적으로 사용 불가");

        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void 느린_응답_타임아웃_테스트() {
        webTestClient.get()
            .uri("/user/test/delay")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody()
            .jsonPath("$.message").isEqualTo("서비스 일시적으로 사용 불가");

        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls())
            .isGreaterThanOrEqualTo(1);
    }

    @Test
    void 서킷브레이커_상태_전환_시나리오_테스트() {
        // 초기 상태 확인
        log.info("초기 상태: {}", circuitBreaker.getState());
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // OPEN 상태로 전환
        for (int i = 0; i < 8; i++) {
            webTestClient.get()
                .uri("/user/test/error")
                .exchange()
                .expectStatus().is5xxServerError();

            log.info("실패 #{} 후 상태: {}, 실패율: {}%, 실패 횟수: {}, 허용되지 않은 호출 횟수: {}",
                i + 1,
                circuitBreaker.getState(),
                circuitBreaker.getMetrics().getFailureRate(),
                circuitBreaker.getMetrics().getNumberOfFailedCalls(),
                circuitBreaker.getMetrics().getNumberOfNotPermittedCalls());
        }

        // OPEN 상태 확인
        log.info("연속 실패 후 상태: {}", circuitBreaker.getState());
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // HALF-OPEN 상태 전환 대기
        log.info("HALF_OPEN 상태 전환 대기 시작");

        // Awaitility를 사용하여 HALF_OPEN 상태로 전환되기를 대기
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                CircuitBreaker.State state = circuitBreaker.getState();
                log.info("현재 상태: {}, 실패율: {}%, 실패 횟수: {}, 허용되지 않은 호출 횟수: {}",
                    state,
                    circuitBreaker.getMetrics().getFailureRate(),
                    circuitBreaker.getMetrics().getNumberOfFailedCalls(),
                    circuitBreaker.getMetrics().getNumberOfNotPermittedCalls());
                assertThat(state).isEqualTo(CircuitBreaker.State.HALF_OPEN);
            });

        // HALF-OPEN 상태에서 정상 응답으로 CLOSED 상태로 복구
        for (int i = 0; i < 3; i++) {
            webTestClient.get()
                .uri("/user/test/normal")
                .exchange()
                .expectStatus().isOk();
        }

        // CLOSED 상태 확인
        await()
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                CircuitBreaker.State state = circuitBreaker.getState();
                log.info("정상 응답 후 상태: {}, 실패율: {}%, 실패 횟수: {}, 허용되지 않은 호출 횟수: {}",
                    state,
                    circuitBreaker.getMetrics().getFailureRate(),
                    circuitBreaker.getMetrics().getNumberOfFailedCalls(),
                    circuitBreaker.getMetrics().getNumberOfNotPermittedCalls());
                assertThat(state).isEqualTo(CircuitBreaker.State.CLOSED);
            });
    }

    @Test
    void 동시_요청_처리_테스트() {
        int concurrentRequests = 10;

        // Circuit Breaker 초기 상태 확인
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 동시 요청 실행
        StepVerifier.create(
                Flux.range(0, concurrentRequests)
                    .flatMap(i -> webTestClient.get()
                        .uri("/user/test/normal")
                        .exchange()
                        .returnResult(String.class)
                        .getResponseBody())
            )
            .expectNextCount(concurrentRequests)
            .verifyComplete();

        // Circuit Breaker 상태 및 메트릭 확인
        await()
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
                log.info("Circuit Breaker 상태: {}, 성공 호출: {}, 실패 호출: {}, 버퍼 크기: {}",
                    circuitBreaker.getState(),
                    metrics.getNumberOfSuccessfulCalls(),
                    metrics.getNumberOfFailedCalls(),
                    metrics.getNumberOfBufferedCalls());

                assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
                assertThat(metrics.getNumberOfSuccessfulCalls()).isGreaterThanOrEqualTo(8); // 슬라이딩 윈도우 사이즈
                assertThat(metrics.getNumberOfFailedCalls()).isZero();
            });
    }
}