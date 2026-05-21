package com.retaillens.backend.config;

import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder
            .requestFactory(new SimpleClientHttpRequestFactory())  // HTTP/1.1 only
            .build();
    }
}