package com.jeontongjuro.backend.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 Local API 지오코딩 설정(application.yml {@code geo.kakao}).
 * <p>
 * ★수빈 인증 라인의 {@code app.kakao.*}(OAuth)와 의도적으로 분리한다 — 같은 REST 키를 재사용하더라도
 * 파이프라인 지오코딩과 회원 인증은 목적·수명주기가 달라 프로퍼티 네임스페이스를 섞지 않는다.
 * <p>
 * apiKey는 환경변수 {@code KAKAO_REST_API_KEY} 로만 주입되며 코드·로그·예외 메시지에 싣지 않는다.
 * 미주입(blank) 시 8단계에서 명확한 메시지로 중단한다(NPE 아님, {@link KakaoGeocodingClientImpl}).
 */
@ConfigurationProperties(prefix = "geo.kakao")
public record GeoProperties(
        String apiKey,
        String baseUrl
) {
}
