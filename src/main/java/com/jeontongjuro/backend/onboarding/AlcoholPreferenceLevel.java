package com.jeontongjuro.backend.onboarding;

import com.jeontongjuro.backend.auth.exception.AuthException;
import org.springframework.http.HttpStatus;

public enum AlcoholPreferenceLevel {
    LIGHT("가볍게", "7도 미만"),
    MEDIUM("적당히", "7도 이상 20도 미만"),
    STRONG("독하게", "20도 이상");

    private final String label;
    private final String range;

    AlcoholPreferenceLevel(String label, String range) {
        this.label = label;
        this.range = range;
    }

    public String label() {
        return label;
    }

    public String range() {
        return range;
    }

    public static AlcoholPreferenceLevel from(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return valueOf(raw.strip().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 아래 공통 400으로 변환한다.
            }
        }
        throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_ONBOARDING_PREFERENCES",
                "허용되지 않은 도수 취향입니다: '" + raw + "' (허용: LIGHT, MEDIUM, STRONG)");
    }
}
