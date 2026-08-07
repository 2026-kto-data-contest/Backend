package com.jeontongjuro.backend.tour;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TourAPI(한국관광공사 KorService2) 근접 캐싱·매칭 설정(application.yml {@code tour.api}).
 * <p>
 * ★수집(pipeline.collect.*)·지오코딩(geo.kakao.*)·인증(app.kakao.*)과 무충돌 신규 네임스페이스.
 * serviceKey는 환경변수 {@code AT_SERVICE_KEY}(aT·KTO 공용) 로만 주입하며 코드·로그·예외 메시지에
 * 싣지 않는다. 미주입(blank) 시 첫 호출에서 명확한 메시지로 중단한다({@link TourApiClientImpl}).
 * <p>
 * ★{@code radiusM}은 하드코딩하지 않고 여기서만 조정한다(기획 피드백 대비). 20000m 기본이되 값은 프로퍼티.
 * {@code numOfRows}는 100 — 상한 미검증(전제 11)이라 페이징을 상시 경로로 만들어 실호출로 검증한다.
 */
@ConfigurationProperties(prefix = "tour.api")
public record TourProperties(
        String baseUrl,
        String serviceKey,
        int radiusM,
        int numOfRows,
        String mobileApp,
        long throttleMillis
) {
}
