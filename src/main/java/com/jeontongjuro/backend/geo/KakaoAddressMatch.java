package com.jeontongjuro.backend.geo;

import java.math.BigDecimal;

/**
 * 카카오 주소 검색 {@code documents[0]} 채택 결과(클라이언트 → 서비스). 좌표 원문(문자열 x/y)을
 * BigDecimal로 파싱한 값과, 로그·감사용 {@code address_type}만 담는다.
 * <p>
 * ★x=경도(lng)/y=위도(lat) 매핑은 클라이언트에서 이미 끝냈다 — 이 record의 lat/lng 이름이 곧 축이다.
 * 범위 검증(위도 33~39·경도 124~132)은 {@link GeocodingService}가 수행한다.
 */
public record KakaoAddressMatch(
        BigDecimal lat,
        BigDecimal lng,
        String addressType
) {
}
