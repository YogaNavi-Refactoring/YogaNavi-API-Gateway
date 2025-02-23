package com.yoganavi.gateway.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@TestConfiguration
public class TestConfig {

    @Value("${wiremock.server.port}")
    private int wireMockPort;

    private WireMockServer wireMockServer;

    @PostConstruct
    void postConstruct() {
        wireMockServer = new WireMockServer(
            WireMockConfiguration.wireMockConfig().port(wireMockPort)
        );
        wireMockServer.start();

        // 정상 응답 설정
        wireMockServer.stubFor(get(urlEqualTo("/user/test/normal"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/plain")
                .withBody("서킷 브레이커 테스트 - 일반 응답")));

        // 에러 응답 설정
        wireMockServer.stubFor(get(urlEqualTo("/user/test/error"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\": \"서버 에러 발생\"}")));

        // 지연 응답 설정
        wireMockServer.stubFor(get(urlEqualTo("/user/test/delay"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(4000)  // 4초 지연
                .withHeader("Content-Type", "text/plain")
                .withBody("지연 응답")));
    }

    @PreDestroy
    void preDestroy() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Bean
    public WireMockServer wireMockServer() {
        return wireMockServer;
    }
}