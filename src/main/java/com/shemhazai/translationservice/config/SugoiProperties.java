package com.shemhazai.translationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "sugoi")
public record SugoiProperties(
        String websocketUrl,
        Duration connectTimeout
) {
    public SugoiProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (websocketUrl != null && !websocketUrl.isBlank()) {
            URI uri = URI.create(websocketUrl);
            if (!"ws".equalsIgnoreCase(uri.getScheme()) && !"wss".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("sugoi.websocket-url must use ws:// or wss://");
            }
        }
    }
}
