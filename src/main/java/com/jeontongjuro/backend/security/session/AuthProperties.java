package com.jeontongjuro.backend.security.session;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Duration sessionDuration, boolean cookieSecure, String cookieSameSite) {

    public AuthProperties {
        if (sessionDuration == null || sessionDuration.isZero() || sessionDuration.isNegative()) {
            throw new IllegalArgumentException("app.auth.session-duration은 0보다 커야 합니다.");
        }
        if (cookieSameSite == null || cookieSameSite.isBlank()) {
            throw new IllegalArgumentException("app.auth.cookie-same-site는 비어 있을 수 없습니다.");
        }
        cookieSameSite = switch (cookieSameSite.trim().toLowerCase()) {
            case "lax" -> "Lax";
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> throw new IllegalArgumentException(
                    "app.auth.cookie-same-site는 Lax, Strict, None 중 하나여야 합니다.");
        };
        if ("None".equals(cookieSameSite) && !cookieSecure) {
            throw new IllegalArgumentException(
                    "app.auth.cookie-same-site=None은 app.auth.cookie-secure=true가 필요합니다.");
        }
    }
}
