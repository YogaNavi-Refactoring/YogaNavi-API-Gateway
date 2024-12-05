package com.yoganavi.gateway.config;

import com.yoganavi.gateway.constants.SecurityConstants;
import com.yoganavi.gateway.filter.JwtAuthenticationFilter;
import com.yoganavi.gateway.filter.RequestLoggingFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;

import java.util.ArrayList;

@Configuration
public class RouteConfig {

    @Value("${service.url.user}")
    private String userServiceUrl;

    @Value("${service.url.live-lecture}")
    private String liveLectureServiceUrl;

    @Value("${service.url.recorded-lecture}")
    private String recordedLectureServiceUrl;

    @Value("${service.url.signaling}")
    private String signalingServiceUrl;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RequestLoggingFilter requestLoggingFilter;

    public RouteConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                       RequestLoggingFilter requestLoggingFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.requestLoggingFilter = requestLoggingFilter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        // JWT 인증 필요없는 공개 경로
        JwtAuthenticationFilter.Config authConfig = new JwtAuthenticationFilter.Config()
                // User Service 공개 API
                .addExcludedPath("/user/login")
                .addExcludedPath("/user/register/email")
                .addExcludedPath("/user/register/authnumber")
                .addExcludedPath("/user/register")
                .addExcludedPath("/user/find-password/email")
                .addExcludedPath("/user/find-password/authnumber")
                .addExcludedPath("/user/find-password")
                // Recorded Lecture Service 공개 API
                .addExcludedPath("/recorded-lecture/sort/**")
                .addExcludedPath("/recorded-lecture/mypage/update/**");

        return builder.routes()
                // User Service 공개 라우트
                .route("user-service-public", r -> r
                        .path("/user/login", "/user/register/**", "/user/find-password/**")
                        .filters(f -> f.filter(requestLoggingFilter.apply(new Object())))
                        .uri(userServiceUrl))

                // User Service 토큰 재발급
                .route("user-token-refresh", r -> r
                        .path("/user/token")
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .filter((exchange, chain) -> {
                                    ServerHttpRequest request = exchange.getRequest();
                                    String refreshToken = getRefreshTokenFromCookies(exchange);

                                    if (refreshToken != null) {
                                        ServerHttpRequest modifiedRequest = new ServerHttpRequestDecorator(request) {
                                            @Override
                                            public HttpHeaders getHeaders() {
                                                HttpHeaders headers = new HttpHeaders();
                                                super.getHeaders().forEach((key, values) -> headers.put(key, new ArrayList<>(values)));
                                                headers.set(SecurityConstants.REFRESH_TOKEN_HEADER, refreshToken);
                                                return headers;
                                            }
                                        };
                                        return chain.filter(exchange.mutate().request(modifiedRequest).build());
                                    }
                                    return chain.filter(exchange);
                                }))
                        .uri(userServiceUrl))

                // User Service 보호된 라우트
                .route("user-service-protected", r -> r
                        .path("/user/**", "/settings/**")
                        .and()
                        .not(p -> p.path("/user/login", "/user/register/**", "/user/find-password/**"))
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .filter(jwtAuthenticationFilter.apply(authConfig)))
                        .uri(userServiceUrl))

                // Live Lecture Service 공개 라우트
                .route("live-lecture-service-public", r -> r
                        .path("/live-lecture/sort/**", "/live-lecture/search/**")
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .circuitBreaker(config -> config
                                        .setName("live-lecture-service")
                                        .setFallbackUri("/fallback/live-lecture"))
                                .retry(3))
                        .uri(liveLectureServiceUrl))

                // Live Lecture Service 보호된 라우트
                .route("live-lecture-service-protected", r -> r
                        .path("/live-lecture/**")
                        .and()
                        .not(p -> p.path("/live-lecture/sort/**", "/live-lecture/search/**"))
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .filter(jwtAuthenticationFilter.apply(authConfig))
                                .circuitBreaker(config -> config
                                        .setName("live-lecture-service")
                                        .setFallbackUri("/fallback/live-lecture"))
                                .retry(3))
                        .uri(liveLectureServiceUrl))

                // Recorded Lecture Service 공개 라우트
                .route("recorded-lecture-service-public", r -> r
                        .path("/recorded-lecture/sort/**", "/recorded-lecture/search/**", "/recorded-lecture/mypage/update/**")
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .circuitBreaker(config -> config
                                        .setName("recorded-lecture-service")
                                        .setFallbackUri("/fallback/recorded-lecture"))
                                .retry(3))
                        .uri(recordedLectureServiceUrl))

                // Recorded Lecture Service 보호된 라우트
                .route("recorded-lecture-service-protected", r -> r
                        .path("/recorded-lecture/**")
                        .and()
                        .not(p -> p.path("/recorded-lecture/sort/**", "/recorded-lecture/search/**", "/recorded-lecture/mypage/update/**"))
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .filter(jwtAuthenticationFilter.apply(authConfig))
                                .circuitBreaker(config -> config
                                        .setName("recorded-lecture-service")
                                        .setFallbackUri("/fallback/recorded-lecture"))
                                .retry(3))
                        .uri(recordedLectureServiceUrl))

                // Signaling Service (모두 보호됨)
                .route("signaling-service", r -> r
                        .path("/signaling/**")
                        .filters(f -> f
                                .filter(requestLoggingFilter.apply(new Object()))
                                .filter(jwtAuthenticationFilter.apply(authConfig))
                                .circuitBreaker(config -> config
                                        .setName("signaling-service")
                                        .setFallbackUri("/fallback/signaling"))
                                .retry(3))
                        .uri(signalingServiceUrl))
                .build();
    }

    private String getRefreshTokenFromCookies(ServerWebExchange exchange) {
        if (exchange.getRequest().getCookies().containsKey(SecurityConstants.REFRESH_TOKEN_COOKIE)) {
            return exchange.getRequest().getCookies()
                    .getFirst(SecurityConstants.REFRESH_TOKEN_COOKIE).getValue();
        }
        return null;
    }
}
