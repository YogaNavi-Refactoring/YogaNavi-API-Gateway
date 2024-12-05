package com.yoganavi.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .requestCache(cache -> cache.disable()) // 요청 캐시 비활성화
                .securityContextRepository(
                        NoOpServerSecurityContextRepository.getInstance()) // 서버 세션 비활성화
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()
                )
                .build();
    }
}
