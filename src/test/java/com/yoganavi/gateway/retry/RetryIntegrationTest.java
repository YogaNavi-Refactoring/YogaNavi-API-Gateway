package com.yoganavi.gateway.retry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RetryIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void 일시적_실패_후_성공_테스트() {
        webTestClient.get()
            .uri("/user/test/retry/temporary-fail")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo("리트라이 테스트 - 일시적 실패 후 성공");
    }

    @Test
    void 영구적_실패_최대_재시도_테스트() {
        webTestClient.get()
            .uri("/user/test/retry/permanent-fail")
            .exchange()
            .expectStatus().is5xxServerError();
    }

    @Test
    void BadGateway_재시도_테스트() {
        webTestClient.get()
            .uri("/user/test/retry/bad-gateway")
            .exchange()
            .expectStatus().is5xxServerError();
    }
}