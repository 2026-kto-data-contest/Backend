package com.jeontongjuro.backend.auth.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendBaseUrl) {

    public String frontendUrl(String path) {
        URI base = URI.create(frontendBaseUrl);
        return base.resolve(path).toString();
    }
}
