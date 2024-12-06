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
        // 공개 경로
        JwtAuthenticationFilter.Config authConfig = new JwtAuthenticationFilter.Config()
                .addExcludedPath("/user/login")
                .addExcludedPath("/user/register/email")
                .addExcludedPath("/user/register/authnumber")
                .addExcludedPath("/user/register")
                .addExcludedPath("/user/find-password/email")
                .addExcludedPath("/user/find-password/authnumber")
                .addExcludedPath("/user/find-password");

        return builder.routes()
                // UseService 공개 라우트
                .route("user-service-public", r -> r
                        .path("/user/login", "/user/register/**", "/user/find-password/**")
                        .filters(f -> f.filter(requestLoggingFilter.apply(new Object())))
                        .uri("lb://user-service"))

                // UserService 보호된 라우트
                .route("user-service-protected", r -> r
                        .path("/user/**", "/settings/**")
                        .and()
                        .not(p -> p.path("/user/login", "/user/register/**", "/user/find-password/**"))
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .filter(jwtAuthenticationFilter.apply(authConfig)))
                        .uri("lb://user-service"))

                // LiveLectureService
                .route("live-lecture-service", r -> r
                        .path("/live-lecture/**")
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .filter(jwtAuthenticationFilter.apply(authConfig))
                                .circuitBreaker(config -> config
                                        .setName("live-lecture-service")
                                        .setFallbackUri("/fallback/live-lecture"))
                                .retry(3))
                        .uri("lb://live-lecture-service"))

                // RecordedLectureService
                .route("recorded-lecture-service", r -> r
                        .path("/recorded-lecture/**")
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .filter(jwtAuthenticationFilter.apply(authConfig))
                                .circuitBreaker(config -> config
                                        .setName("recorded-lecture-service")
                                        .setFallbackUri("/fallback/recorded-lecture"))
                                .retry(3))
                        .uri("lb://recorded-lecture-service"))

                // SignalingService
                .route("signaling-service", r -> r
                        .path("/signaling/**")
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .filter(jwtAuthenticationFilter.apply(authConfig))
                                .circuitBreaker(config -> config
                                        .setName("signaling-service")
                                        .setFallbackUri("/fallback/signaling"))
                                .retry(3))
                        .uri("lb://signaling-service"))
                .build();
    }
}