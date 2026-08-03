package com.jeontongjuro.backend.auth.kakao;

import com.jeontongjuro.backend.auth.exception.AuthException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;

@ConfigurationProperties(prefix = "app.kakao")
public record KakaoProperties(String restApiKey, String clientSecret, String redirectUri) {

    public void validateConfigured() {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_NOT_CONFIGURED",
                    "카카오 로그인 환경설정이 필요합니다.");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_NOT_CONFIGURED",
                    "카카오 로그인 환경설정이 필요합니다.");
        }
    }
}
