package com.yoganavi.gateway.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 게이트웨이를 통과하는 모든 요청의 메트릭 수집
 */
@Component
@Slf4j
public class MetricFilter extends AbstractGatewayFilterFactory<Object> {

    private final MeterRegistry meterRegistry;

    public MetricFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * GatewayFilter 구현 각 요청별 처리 시간 및 결과를 측정 및 기록
     *
     * @return GatewayFilter
     */
    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            long startTime = System.currentTimeMillis();
            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();

            return chain.filter(exchange)
                .doFinally(signalType -> {
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    // 응답시간 기록
                    Timer.builder("gateway.request.duration")
                        .tag("path", path)
                        .tag("method", method)
                        .tag("status", exchange.getResponse().getStatusCode().toString())
                        .register(meterRegistry)
                        .record(duration, TimeUnit.MILLISECONDS);

                    // 요청 수 카운트
                    Counter.builder("gateway.request.count")
                        .tag("path", path)
                        .tag("method", method)
                        .tag("status", exchange.getResponse().getStatusCode().toString())
                        .register(meterRegistry)
                        .increment();

                    log.info("Request: {} {} 가 {}ms 안에 완료됨. 상태 코드: {}",
                        method, path, duration,
                        exchange.getResponse().getStatusCode());
                });
        };
    }

}