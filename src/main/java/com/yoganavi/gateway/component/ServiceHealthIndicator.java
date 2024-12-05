package com.yoganavi.gateway.component;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 서비스 헬스체크 수행
 * 서비스의 actuator/health 엔드포인트 호출하여 상태 확인
 */
@Component
@Slf4j
public class ServiceHealthIndicator implements HealthIndicator {

    private final WebClient webClient;
    private final Map<String, String> services = new HashMap<>();

    public ServiceHealthIndicator(WebClient webClient) {
        this.webClient = webClient;
    }

    @Value("${service.url.user}")
    public void setUserServiceUrl(String url) {
        services.put("user-service", url);
    }

    @Value("${service.url.live-lecture}")
    public void setLiveLectureServiceUrl(String url) {
        services.put("live-lecture-service", url);
    }

    @Value("${service.url.recorded-lecture}")
    public void setRecordedLectureServiceUrl(String url) {
        services.put("recorded-lecture-service", url);
    }

    @Value("${service.url.signaling}")
    public void setSignalingServiceUrl(String url) {
        services.put("signaling-service", url);
    }

    @Override
    public Health health() {
        Map<String, Health> healthResults = new HashMap<>();

        services.forEach((serviceName, baseUrl) -> {
            try {
                Health serviceHealth = checkServiceHealth(baseUrl + "/actuator/health");
                healthResults.put(serviceName, serviceHealth);
                log.info("서비스: {}의 헬스체크 결과: {}", serviceName,
                        serviceHealth.getStatus());
            } catch (Exception e) {
                log.warn("서비스: {}의 헬스체크 실패", serviceName, e);
                healthResults.put(serviceName, Health.down(e).build());
            }
        });

        // 모든 서비스가 UP 상태인지 확인
        boolean allUp = healthResults.values().stream()
                .allMatch(health -> Status.UP.equals(health.getStatus()));

        return allUp ?
                Health.up().withDetails(healthResults).build() :
                Health.down().withDetails(healthResults).build();
    }

    private Health checkServiceHealth(String healthUrl) {
        try {
            webClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(3));

            return Health.up().build();
        } catch (Exception e) {
            log.warn("URL: {}에 대한 헬스체크 실패", healthUrl, e);
            return Health.down(e).build();
        }
    }
}