package com.yoganavi.gateway.config;

import com.yoganavi.gateway.filter.JwtAuthenticationFilter;
import com.yoganavi.gateway.filter.RequestLoggingFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RequestLoggingFilter requestLoggingFilter;

    public RouteConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
        RequestLoggingFilter requestLoggingFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.requestLoggingFilter = requestLoggingFilter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        // 토큰 검증 필요 없음
        JwtAuthenticationFilter.Config authConfig = new JwtAuthenticationFilter.Config()
            .addExcludedPath("/user/test/**")
            .addExcludedPath("/user/fallback/test/**")
            .addExcludedPath("/user/login")
            .addExcludedPath("/user/register/**")
            .addExcludedPath("/user/edit/credential/**");

        return builder.routes()
            // UserService - 공개
            .route("user-service-public", r -> r
                .path("/user/test/**", "/user/fallback/test/**",
                    "/user/login", "/user/register/**",
                    "/user/edit/credential/**")
                .filters(f -> f
                    .filter(requestLoggingFilter.apply(new Object()))
                    .circuitBreaker(config -> config
                        .setName("user-service-test")
                        .setFallbackUri("forward:/user/fallback/test"))
                    .retry(3))
                .uri("lb://user-service"))

            // UserService - 보호
            .route("user-service-protected", r -> r
                .path("/user/**")
                .and()
                .not(p -> p.path("/user/test/**", "/user/fallback/test/**",
                    "/user/login", "/user/register/**",
                    "/user/edit/credential/**"))
                .filters(f -> f
                    .filter(requestLoggingFilter.apply(new Object()))
                    .filter(jwtAuthenticationFilter.apply(authConfig))
                    .circuitBreaker(config -> config
                        .setName("user-service")
                        .setFallbackUri("/user/fallback"))
                    .retry(3))
                .uri("lb://user-service"))

            // LiveLectureService
            .route("live-lecture-service", r -> r
                .path("/live-lecture/**")
                .filters(f -> f
                    .filter(requestLoggingFilter.apply(new Object()))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                    .circuitBreaker(config -> config
                        .setName("live-lecture-service")
                        .setFallbackUri("/fallback/live-lecture"))
                    .retry(3))
                .uri("lb://lecture-service"))

            // RecordedLectureService
            .route("recorded-lecture-service", r -> r
                .path("/recorded-lecture/**")
                .filters(f -> f
                    .filter(requestLoggingFilter.apply(new Object()))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                    .circuitBreaker(config -> config
                        .setName("recorded-lecture-service")
                        .setFallbackUri("/fallback/recorded-lecture"))
                    .retry(3))
                .uri("lb://lecture-service"))

            // LectureService
            .route("lecture-service", r -> r
                .path("/lecture/**")
                .filters(f -> f
                    .filter(requestLoggingFilter.apply(new Object()))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                    .circuitBreaker(config -> config
                        .setName("lecture-service")
                        .setFallbackUri("/fallback/lecture"))
                    .retry(3))
                .uri("lb://lecture-service"))

            // SignalingService
            .route("signaling-service", r -> r
                .path("/signaling/**")
                .filters(f -> f
                    .filter(requestLoggingFilter.apply(new Object()))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                    .circuitBreaker(config -> config
                        .setName("signaling-service")
                        .setFallbackUri("/fallback/signaling"))
                    .retry(3))
                .uri("lb://signaling-service"))
            .build();
    }
}