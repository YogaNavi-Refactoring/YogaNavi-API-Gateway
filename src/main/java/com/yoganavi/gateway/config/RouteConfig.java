package com.yoganavi.gateway.config;

import com.yoganavi.gateway.filter.CustomFallbackFilter;
import com.yoganavi.gateway.filter.JwtAuthenticationFilter;
import com.yoganavi.gateway.filter.RequestLoggingFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class RouteConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RequestLoggingFilter requestLoggingFilter;
    private final CustomFallbackFilter customFallbackFilter;

    public RouteConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
        RequestLoggingFilter requestLoggingFilter, CustomFallbackFilter customFallbackFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.requestLoggingFilter = requestLoggingFilter;
        this.customFallbackFilter = customFallbackFilter;
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
                    .filter(customFallbackFilter.apply(new CustomFallbackFilter.Config()))
                    .circuitBreaker(config -> config
                        .setName("user-service-test")
                        .addStatusCode("500"))
                    .retry(config -> config
                        .setRetries(3)
                        .setStatuses(HttpStatus.BAD_GATEWAY)))
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
                    .filter(customFallbackFilter.apply(new CustomFallbackFilter.Config()))
                    .circuitBreaker(config -> config
                        .setName("user-service")
                        .addStatusCode("500"))
                    .retry(config -> config
                        .setRetries(3)
                        .setStatuses(HttpStatus.BAD_GATEWAY)))
                .uri("lb://user-service"))

            // LiveLectureService
            .route("live-lecture-service", r -> r
                .path("/live-lecture/**")
                .filters(f -> f
                    .filter(requestLoggingFilter.apply(new Object()))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                    .filter(customFallbackFilter.apply(new CustomFallbackFilter.Config()))
                    .circuitBreaker(config -> config
                        .setName("live-lecture-service")
                        .addStatusCode("500"))
                    .retry(config -> config
                        .setRetries(3)
                        .setStatuses(HttpStatus.BAD_GATEWAY)))
                .uri("lb://lecture-service"))

            // RecordedLectureService
            .route("recorded-lecture-service", r -> r
                .path("/recorded-lecture/**")
                .filters(f -> f
                    .filter(requestLoggingFilter.apply(new Object()))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                    .filter(customFallbackFilter.apply(new CustomFallbackFilter.Config()))
                    .circuitBreaker(config -> config
                        .setName("recorded-lecture-service")
                        .addStatusCode("500"))
                    .retry(config -> config
                        .setRetries(3)
                        .setStatuses(HttpStatus.BAD_GATEWAY)))
                .uri("lb://lecture-service"))

            // LectureService
            .route("lecture-service", r -> r
                .path("/lecture/**")
                .filters(f -> f
                    .filter(requestLoggingFilter.apply(new Object()))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                    .filter(customFallbackFilter.apply(new CustomFallbackFilter.Config()))
                    .circuitBreaker(config -> config
                        .setName("lecture-service")
                        .addStatusCode("500"))
                    .retry(config -> config
                        .setRetries(3)
                        .setStatuses(HttpStatus.BAD_GATEWAY)))
                .uri("lb://lecture-service"))

            // SignalingService
            .route("signaling-service", r -> r
                .path("/signaling/**")
                .filters(f -> f
                    .filter(requestLoggingFilter.apply(new Object()))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                    .filter(customFallbackFilter.apply(new CustomFallbackFilter.Config()))
                    .circuitBreaker(config -> config
                        .setName("signaling-service")
                        .addStatusCode("500"))
                    .retry(config -> config
                        .setRetries(3)
                        .setStatuses(HttpStatus.BAD_GATEWAY)))
                .uri("lb://signaling-service"))
            .build();
    }
}