package com.jeontongjuro.backend.security.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("app.cors.allowed-origins는 하나 이상 필요합니다.");
        }
        allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(CorsProperties::normalizeOrigin)
                .distinct()
                .toList();
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("app.cors.allowed-origins는 하나 이상 필요합니다.");
        }
    }

    private static String normalizeOrigin(String value) {
        URI uri = URI.create(value);
        if (uri.getScheme() == null || uri.getAuthority() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty())) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins에는 경로 없는 origin만 입력해야 합니다: " + value);
        }
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
