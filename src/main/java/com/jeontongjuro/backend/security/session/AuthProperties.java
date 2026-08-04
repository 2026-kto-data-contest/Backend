package com.jeontongjuro.backend.security.session;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Duration sessionDuration, boolean cookieSecure) {

    public AuthProperties {
        if (sessionDuration == null || sessionDuration.isZero() || sessionDuration.isNegative()) {
            throw new IllegalArgumentException("app.auth.session-duration은 0보다 커야 합니다.");
        }
    }
}
