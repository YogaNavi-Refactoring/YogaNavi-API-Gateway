package com.yoganavi.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient 설정
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                .responseTimeout(Duration.ofSeconds(5))))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }
}