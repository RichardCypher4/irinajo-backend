package com.irinajo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpConfig {

    /** Shared HTTP client for outbound calls (SMS, geocoding, weather, push). Short timeouts keep p95 low. */
    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(10_000);
        return RestClient.builder().requestFactory(f).build();
    }
}
