package com.jeontongjuro.backend.auth.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendBaseUrl, List<String> frontendAllowedOrigins) {

    public AppProperties(String frontendBaseUrl) {
        this(frontendBaseUrl, List.of(frontendBaseUrl));
    }

    public AppProperties {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            throw new IllegalArgumentException("app.frontend-base-url은 비어 있을 수 없습니다.");
        }
        URI base = URI.create(frontendBaseUrl);
        if (base.getScheme() == null || base.getAuthority() == null) {
            throw new IllegalArgumentException("app.frontend-base-url은 유효한 URL이어야 합니다.");
        }
        frontendAllowedOrigins = frontendAllowedOrigins == null ? List.of() : frontendAllowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(AppProperties::originOf)
                .distinct()
                .toList();
        if (frontendAllowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("app.frontend-allowed-origins는 하나 이상 필요합니다.");
        }
    }

    public String frontendUrl(String path) {
        if (isAllowedAbsoluteUrl(path)) {
            return path;
        }
        URI base = URI.create(frontendBaseUrl);
        return base.resolve(path).toString();
    }

    public String safeReturnTo(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        if (value.startsWith("/") && !value.startsWith("//") && !value.contains("\\")) {
            return value;
        }
        return isAllowedAbsoluteUrl(value) ? value : "/";
    }

    private boolean isAllowedAbsoluteUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getUserInfo() == null
                    && uri.getRawFragment() == null
                    && frontendAllowedOrigins.contains(originOf(uri));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String originOf(String value) {
        URI uri = URI.create(value);
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("프론트 origin 형식이 올바르지 않습니다: " + value);
        }
        return originOf(uri);
    }

    private static String originOf(URI uri) {
        if (uri.getScheme() == null || uri.getAuthority() == null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("프론트 origin 형식이 올바르지 않습니다: " + uri);
        }
        return uri.getScheme().toLowerCase() + "://" + uri.getAuthority().toLowerCase();
    }
}
